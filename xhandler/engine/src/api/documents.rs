//! Operations on documents that don't contact SUNAT: validate (XSD + rules),
//! and inspect.

use axum::extract::Json;
use serde::{Deserialize, Serialize};
use xhandler::prelude::UblFile;

use super::{blocking, lima_today, ApiError, ApiJson, ErrorKind};
use crate::inspect::{self, InspectResponse};
use crate::rules::{self, Finding};
use crate::xmldsig;
use crate::xsd::{self, XsdOutcome};

/// SUNAT's code for an XML it can't parse, "incluye validación de XSD"
/// (AjustesValidacionesCPE, General sheet)
const UNPARSEABLE: &str = "0306";
/// SUNAT's code for an altered document (a signature that doesn't verify)
const ALTERED: &str = "2335";
/// Code of the XSD violations (SUNAT reports them all as 0306)
const XSD: &str = "XSD";
/// libxml2's diagnostic for an `ext:ExtensionContent` without children
const EMPTY_SIGNATURE_SLOT: &str = "}ExtensionContent': Missing child element(s)";

// --- validate ---------------------------------------------------------------

#[derive(Deserialize)]
pub struct ValidateRequest {
    pub xml: String,
}

#[derive(Serialize, Debug)]
pub struct ValidateResponse {
    pub valid: bool,
    pub errors: Vec<Finding>,
    pub observations: Vec<Finding>,
}

pub(super) async fn validate(
    ApiJson(request): ApiJson<ValidateRequest>,
) -> Result<Json<ValidateResponse>, ApiError> {
    let unparseable = |detail: &str| {
        Json(ValidateResponse {
            valid: false,
            errors: vec![Finding {
                code: UNPARSEABLE.into(),
                message: format!("No se puede leer (parsear) el archivo XML: {detail}"),
            }],
            observations: Vec::new(),
        })
    };
    if request.xml.len() > xsd::MAX_XML_BYTES {
        return Ok(unparseable("el archivo es demasiado grande"));
    }
    // One quick pass refuses DOCTYPEs and reads what the rules need
    let facts = match inspect::read_facts(&request.xml) {
        Ok(facts) => facts,
        Err(detail) => return Ok(unparseable(&detail)),
    };
    // The bytes as sent to SUNAT (in the encoding the XML declares)
    let bytes = UblFile::from(request.xml).bytes().to_vec();
    // A signed document must verify: SUNAT rejects an altered one (2335)
    let signature = if facts.digest_value.is_some() {
        let signed = bytes.clone();
        Some(
            blocking(move || Ok::<_, ApiError>(xmldsig::verify(&signed)))
                .await?
                .0,
        )
    } else {
        None
    };
    let mut errors = match xsd::validate(&facts.document_type, bytes)
        .await
        .map_err(|e| ApiError::new(ErrorKind::Other, e))?
    {
        XsdOutcome::Valid | XsdOutcome::NoSchema => Vec::new(),
        XsdOutcome::Unparseable => return Ok(unparseable("XML mal formado")),
        XsdOutcome::Invalid(messages) => messages
            .into_iter()
            // Documents are validated before signing (DESIGN.md §4): the
            // empty signature slot of an unsigned document is fine
            .filter(|message| {
                facts.digest_value.is_some() || !message.contains(EMPTY_SIGNATURE_SLOT)
            })
            .map(|message| Finding {
                code: XSD.into(),
                message,
            })
            .collect(),
    };
    let (rule_errors, observations) = rules::check(&facts, lima_today());
    errors.extend(rule_errors);
    match signature {
        None | Some(xmldsig::Verdict::Valid) => {}
        Some(xmldsig::Verdict::DigestMismatch) => errors.push(Finding {
            code: ALTERED.into(),
            message: "El documento electrónico ingresado ha sido alterado (el resumen no coincide con la firma)".into(),
        }),
        Some(xmldsig::Verdict::BadSignature) => errors.push(Finding {
            code: ALTERED.into(),
            message: "La firma digital no corresponde al certificado del documento".into(),
        }),
        Some(xmldsig::Verdict::Unsupported(reason)) => errors.push(Finding {
            code: ALTERED.into(),
            message: format!("No se puede verificar la firma digital: {reason}"),
        }),
    }
    Ok(Json(ValidateResponse {
        valid: errors.is_empty(),
        errors,
        observations,
    }))
}

// --- inspect ----------------------------------------------------------------

#[derive(Deserialize)]
pub struct InspectRequest {
    pub xml: String,
}

pub(super) async fn inspect(
    ApiJson(request): ApiJson<InspectRequest>,
) -> Result<Json<InspectResponse>, ApiError> {
    blocking(move || {
        inspect::inspect(&request.xml).map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))
    })
    .await
}

#[cfg(test)]
mod tests {
    use super::super::tests::{build_invoice, invoice, post, sign_with_demo_key};
    use axum::http::StatusCode;

    async fn validate(xml: &serde_json::Value) -> serde_json::Value {
        let (status, body) = post("/v1/validate", serde_json::json!({ "xml": xml })).await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body
    }

    async fn build(document: serde_json::Value) -> serde_json::Value {
        let (status, body) = post(
            "/v1/build",
            serde_json::json!({ "document": document, "fecha_emision": super::lima_today() }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body["xml"].clone()
    }

    #[tokio::test]
    async fn built_and_signed_invoice_is_valid() {
        let xml = build(invoice()).await;
        let body = validate(&xml).await;
        assert_eq!(body["valid"], true, "{body}");
        let signed = sign_with_demo_key(xml).await;
        let body = validate(&signed["xml"]).await;
        assert_eq!(body["valid"], true, "{body}");
    }

    #[tokio::test]
    async fn altered_documents_and_signatures_are_2335() {
        let signed = sign_with_demo_key(build(invoice()).await).await;
        let xml = signed["xml"].as_str().unwrap().to_string();
        // A changed line after signing
        let altered = xml.replacen("Item1", "Item9", 1);
        assert_ne!(altered, xml);
        let body = validate(&altered.into()).await;
        assert_eq!(body["valid"], false);
        assert_eq!(body["errors"][0]["code"], "2335", "{body}");
        assert!(body["errors"][0]["message"]
            .as_str()
            .unwrap()
            .contains("alterado"));
        // A forged signature value
        let start = xml.find("<ds:SignatureValue>").unwrap() + "<ds:SignatureValue>".len();
        let mut forged = xml.clone();
        forged.replace_range(
            start..start + 4,
            if &xml[start..start + 4] == "AAAA" {
                "BBBB"
            } else {
                "AAAA"
            },
        );
        let body = validate(&forged.into()).await;
        assert_eq!(body["errors"][0]["code"], "2335", "{body}");
        assert!(
            body["errors"][0]["message"]
                .as_str()
                .unwrap()
                .contains("certificado"),
            "{body}"
        );
    }

    #[tokio::test]
    async fn xsd_violations_and_rules_are_reported() {
        let xml = build(invoice()).await;
        let xml = xml.as_str().unwrap().replacen(
            "<cbc:IssueDate>",
            "<cbc:Unexpected>x</cbc:Unexpected><cbc:IssueDate>",
            1,
        );
        let body = validate(&xml.into()).await;
        assert_eq!(body["valid"], false);
        assert_eq!(body["errors"][0]["code"], "XSD", "{body}");

        let mut document = invoice();
        document["spec"]["serie_numero"] = "X001-1".into();
        document["spec"]["fecha_emision"] = "2099-01-01".into();
        let (_, built) = post("/v1/build", serde_json::json!({ "document": document })).await;
        let body = validate(&built["xml"]).await;
        let codes: Vec<&str> = body["errors"]
            .as_array()
            .unwrap()
            .iter()
            .map(|e| e["code"].as_str().unwrap())
            .collect();
        assert!(codes.contains(&"2329"), "{body}");
    }

    #[tokio::test]
    async fn unparseable_and_doctype_are_0306() {
        for xml in [
            "<Invoice>",
            r#"<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><Invoice>&e;</Invoice>"#,
        ] {
            let body = validate(&xml.into()).await;
            assert_eq!(body["valid"], false);
            assert_eq!(body["errors"][0]["code"], "0306", "{body}");
        }
    }

    #[tokio::test]
    async fn other_document_types_validate() {
        let summary = serde_json::json!({
            "kind": "SummaryDocuments",
            "spec": {
                "numero": 1,
                "fecha_emision_comprobantes": "2026-11-14",
                "proveedor": { "ruc": "12345678912", "razon_social": "Softgreen S.A.C." },
                "comprobantes": [{
                    "tipo_operacion": "1",
                    "comprobante": {
                        "tipo_comprobante": "03",
                        "serie_numero": "B001-1",
                        "cliente": { "tipo_documento_identidad": "1", "numero_documento_identidad": "12345678" },
                        "valor_venta": { "importe_total": 118, "gravado": 100 },
                        "impuestos": { "igv": 18, "igv_tasa": 18 }
                    }
                }]
            }
        });
        let body = validate(&build(summary).await).await;
        assert_eq!(body["valid"], true, "{body}");

        let mut credit_note = serde_json::json!({
            "kind": "CreditNote",
            "spec": {
                "serie_numero": "FC01-1",
                "comprobante_afectado_serie_numero": "F001-1",
                "descripcion_sustento": "Anulación de operación",
                "proveedor": { "ruc": "12345678912", "razon_social": "Softgreen S.A.C." },
                "cliente": { "tipo_documento_identidad": "6", "numero_documento_identidad": "12121212121", "nombre": "Carlos Feria" },
                "detalles": [{ "descripcion": "Item1", "cantidad": 10, "precio": 100 }]
            }
        });
        let body = validate(&build(credit_note.clone()).await).await;
        assert_eq!(body["valid"], true, "{body}");
        credit_note["spec"]["serie_numero"] = "ZC01-1".into();
        let body = validate(&build(credit_note).await).await;
        assert_eq!(body["errors"][0]["code"], "1001", "{body}");
    }

    #[tokio::test]
    async fn inspect_reads_a_signed_invoice() {
        let signed = sign_with_demo_key(build_invoice().await["xml"].clone()).await;
        let (status, body) = post("/v1/inspect", serde_json::json!({ "xml": signed["xml"] })).await;
        assert_eq!(status, StatusCode::OK, "{body}");
        assert_eq!(body["document_type"], "Invoice");
        assert_eq!(body["tipo_comprobante"], "01");
        assert_eq!(body["ruc"], "12345678912");
        assert_eq!(body["serie"], "F001");
        assert_eq!(body["numero"], 1);
        assert_eq!(body["serie_numero"], "F001-1");
        assert_eq!(body["fecha_emision"], "2026-11-15");
        assert_eq!(body["moneda"], "PEN");
        assert_eq!(body["total"], "1180.00");
        assert_eq!(body["customer_tipo"], "6");
        assert_eq!(body["customer_numero"], "12121212121");
        assert_eq!(body["signed"], true);
        assert_eq!(body["digest_value"], signed["digest_value"]);
        assert_eq!(body["sha256"].as_str().unwrap().len(), 64);
    }

    #[tokio::test]
    async fn inspect_refuses_doctype() {
        let (status, body) = post(
            "/v1/inspect",
            serde_json::json!({ "xml": "<!DOCTYPE x><Invoice/>" }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_document");
    }
}

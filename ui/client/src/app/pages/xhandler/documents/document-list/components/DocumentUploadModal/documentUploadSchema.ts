import { mixed, object, string } from "yup";

import {
  isUploadFormat,
  parseEmitJson,
  sfsFileName,
  type UploadFormat,
} from "./documentUpload";

/** Every file the console uploads stays under the service's 5 MB limit for UBL XML. */
const MAX_FILE_BYTES = 5 * 1024 * 1024;

const optionalFile = mixed<File>().test(
  "size",
  "El archivo pesa más de 5 MB",
  (file) => !file || file.size <= MAX_FILE_BYTES,
);

const requiredFor = (format: UploadFormat, message: string) =>
  optionalFile.test("required", message, function (file) {
    return (this.parent as { format: string }).format !== format || !!file;
  });

export const documentUploadSchema = object({
  companyId: string().required("Elige la empresa"),
  format: string()
    .required()
    .test("format", "Elige el formato", (value) => isUploadFormat(value)),
  ublFile: requiredFor("ubl", "Elige el archivo XML"),
  cabFile: requiredFor("sfs", "Elige el archivo .CAB o .NOT"),
  detFile: requiredFor("sfs", "Elige el archivo .DET").test(
    "pair",
    "",
    function (detFile) {
      const { format, cabFile } = this.parent as {
        format: string;
        cabFile?: File;
      };
      if (format !== "sfs" || !cabFile || !detFile) return true;
      const named = sfsFileName(cabFile.name, detFile.name);
      return "error" in named
        ? this.createError({ message: named.error })
        : true;
    },
  ),
  jsonText: string()
    .default("")
    .test("json", "", function (text) {
      if ((this.parent as { format: string }).format !== "json") return true;
      if (!text?.trim()) {
        return this.createError({ message: "Pega o sube el JSON" });
      }
      const parsed = parseEmitJson(text);
      return "error" in parsed
        ? this.createError({ message: parsed.error })
        : true;
    }),
});

export interface IDocumentUploadValues {
  companyId: string;
  format: string;
  ublFile?: File;
  cabFile?: File;
  detFile?: File;
  jsonText: string;
}

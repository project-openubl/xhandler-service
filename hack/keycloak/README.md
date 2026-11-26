`xhandler-realm-dev.json`: the shared Keycloak realm `xhandler` for development and tests:
xhandler's Dev Services, `hack/compose.yaml`, the UI's e2e stack and the CI minikube e2e.
Users `alice`, `bob`, `dave` (email not verified), `carol` (platform-support) and `olga`
(platform admin, every searchpe permission), with their name as password; machine client
`acme-erp` / `acme-secret`. Never import it into a real environment.

Every Keycloak realm that calls xhandler-service (dev or real) needs, on each client:

- an **audience mapper** adding `xhandler-service` to access tokens (the API checks `aud`);
- for machine clients (service accounts), the **`client_id` user-session-note mapper**, as in
  Keycloak's built-in `service_account` scope. The API treats a token as a machine client only
  when `client_id` = `azp` and the username is `service-account-<azp>`.

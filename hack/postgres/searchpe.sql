-- searchpe's own database and login (hack/ui-e2e-stack.sh shows the settings to use it)
CREATE ROLE searchpe LOGIN PASSWORD 'searchpe';
CREATE DATABASE searchpe OWNER searchpe;

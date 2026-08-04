# AWS Deployment Plan — Resume Here

Status: **not started yet**. This is a saved runbook to pick back up later — nothing in this plan has been executed. No AWS resources exist, no code changes have been made.

## Context

Goal: practice cloud CI/CD and AWS deployment using this repo's existing backend (6 Spring Boot microservices), as a **one-time learning burst** — not a permanent deployment. This repo already has everything designed for it: Terraform (VPC/RDS/EKS), Helm values, raw K8s manifests, and a GitHub Actions pipeline (`build-and-test.yml` → `deploy-to-eks.yml`) that builds images, pushes to GHCR, and deploys to EKS. The root README explains why it was never applied: a permanent EKS/RDS environment costs real money every month, which isn't worth it for a portfolio project.

The plan reuses everything already built, run as a single deliberate session: apply real AWS infrastructure, patch a few real gaps that would otherwise break the deploy, run the pipeline, verify it actually works end-to-end, then tear everything down completely before stopping for the day. Done this way, the whole exercise costs roughly **$1-2**, not a recurring bill. The two components with zero free tier — the EKS control plane and the NAT Gateway — bill hourly the instant they're created regardless of use, which is why teardown order and promptness matter more here than anything else in the plan.

Repo details confirmed at plan time: this project has its own git remote (`marioazer/banking-app-microservices` on GitHub) independent of the outer `JobStuff` folder. AWS CLI, kubectl, and Helm were already installed on the local machine; AWS CLI was not yet configured with credentials.

## When resuming

Say something like "let's pick the AWS deployment plan back up" and point at this file — it has every step, expected result, and gotcha needed to execute end-to-end.

---

## Part A — Code fixes (do these locally first, before touching AWS)

1. **Flyway gap** — `01-auth-service/pom.xml` and `02-profile-service/pom.xml` are missing the Flyway dependency that `03-account-service`, `03-transaction-service`, and `05-audit-service` already have (confirmed by reading all three poms — this is the block to copy):
   ```xml
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-database-postgresql</artifactId>
   </dependency>
   ```
   Add it to the `<dependencies>` block in both files. Their `db/migration/*.sql` files already exist and are correct — they've just been inert until now, silently masked locally by Hibernate's `ddl-auto: update`. In prod, `ddl-auto: validate` requires the schema to already exist — without this fix, both services will crash-loop against real RDS. No SQL or YAML changes needed. After adding, run `./mvnw test -B` in both directories to confirm nothing breaks before pushing (Flyway will also run during tests — make sure that still passes).

2. **Repo hygiene** — `03-account-service/maven-debug.log` (252KB) is tracked in git by mistake. Remove it and gitignore it in the same commit as #1:
   ```
   git rm 03-account-service/maven-debug.log
   ```

3. **Do not hand-edit secrets/configmap and commit them.** `k8s/02-secrets.yaml` (placeholder base64 values) and `k8s/01-namespace-config.yaml`'s `DB_HOST` (placeholder RDS hostname) must stay as committed placeholders — `deploy-to-eks.yml` does a blanket `kubectl apply -f k8s/` on every run, which would silently re-apply real secrets back to placeholders if you ever removed them, or leak real secrets into git if you hardcoded them. Instead, real values get patched in live via `kubectl` **after** each CI run (Part H) — never committed.

4. **GHCR image visibility** — packages don't exist until the pipeline's first run. After the first successful run (Part G), go to `https://github.com/marioazer/banking-app-microservices` → **Packages** (right sidebar) → open each of the 6 new packages → **Package settings** → **Danger Zone** → **Change visibility** → **Public**. This avoids `ImagePullBackOff` on EKS without needing to add `imagePullSecrets` to any manifest — simplest option for a one-off session.

Commit and push these code fixes now (push again after Part D once AWS infra exists, to actually trigger the pipeline — see Part G).

---

## Part B — AWS account & local machine prep

1. **Billing alarm before anything else.** Console → `https://console.aws.amazon.com/billing/home#/preferences` → enable "Receive Billing Alerts" → Save. Then go to CloudWatch (must be in `us-east-1`) → **Billing** → **Create alarm** → metric `EstimatedCharges` → threshold **$5** (add a second at $20 if you want) → new SNS topic → confirm the email subscription. Note: this metric lags several hours behind real spend — it's a safety net, not a substitute for tearing down promptly.

2. **IAM user (never root keys).** Console → IAM → Users → **Create user** → name `terraform-deployer` → **Attach policies directly** → `AdministratorAccess`. (Least-privilege scoping is worth doing later; for a single torn-down session it's not worth the extra hours of trial-and-error against "access denied" errors.)

3. **Access key** → IAM → Users → `terraform-deployer` → **Security credentials** tab → **Create access key** → choose "Command Line Interface (CLI)" → copy both values immediately (secret is shown once).

4. **Configure AWS CLI:**
   ```
   aws configure
   ```
   Region: `us-east-1` (matches `terraform/variables.tf` default). Verify:
   ```
   aws sts get-caller-identity
   ```
   Expect the `terraform-deployer` user's ARN back, not root.

5. Confirm `terraform/terraform.tfvars` (already exists locally, gitignored via `*.tfvars`) has a real `db_password` set and nothing else — leave it out of git.

---

## Part C — Terraform apply (this is where real billing starts)

```
cd terraform
terraform plan
terraform apply
```
Expect ~15-20 minutes (EKS cluster + node group is the long pole; VPC/RDS finish faster). On completion:
```
terraform output rds_endpoint
terraform output eks_cluster_name
```
**Important:** `rds_endpoint` returns `host:5432` (host and port together). Strip the `:5432` suffix before using it as `DB_HOST` in Part H — the ConfigMap already has a separate `DB_PORT` key, so keeping the port here would produce a malformed JDBC URL.

Sanity check in console: EKS → Clusters shows "Active", RDS → Databases shows "Available", EC2 → Load Balancers is still **empty** (no Load Balancer exists yet — that's created in Part E).

---

## Part D — Point kubectl at the new cluster

```
aws eks update-kubeconfig --name <eks_cluster_name output> --region us-east-1
kubectl get nodes
```
Expect 2 nodes `Ready` (t3.medium).

---

## Part E — Namespace + Helm installs (before triggering CI)

Order matters — do these before the pipeline deploys the app pods, so Kafka/Redis are already up when the apps first try to connect.

```
kubectl apply -f k8s/01-namespace-config.yaml

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm install ingress-nginx ingress-nginx/ingress-nginx -f helm/02-ingress-nginx-values.yaml -n banking-app
helm install kafka bitnami/kafka -f helm/01-kafka-values.yaml -n banking-app
helm install redis bitnami/redis -f helm/03-redis-values.yaml -n banking-app
```

**Release names matter**: the app's env vars (confirmed in `k8s/07-notification-service.yaml`) hardcode `kafka.banking-app.svc.cluster.local:9092` and `redis-master.banking-app.svc.cluster.local` — so the Helm release names must be exactly `kafka` and `redis`.

Verify each before moving on:
- `kubectl get svc -n banking-app ingress-nginx-controller` — `EXTERNAL-IP` goes from `<pending>` to a real NLB hostname within a few minutes. **Note this in the console (EC2 → Load Balancers) — you'll need to confirm this exact object is deleted before `terraform destroy` in Part J.**
- `kubectl get pods -n banking-app -l app.kubernetes.io/instance=kafka` — `kafka-0` and `kafka-zookeeper-0` both `Running`.
- `kubectl get pods -n banking-app -l app.kubernetes.io/instance=redis` — `redis-master-0` `Running`.

---

## Part F — GitHub Actions secrets

Repo → **Settings → Secrets and variables → Actions → New repository secret**:
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` — from the `terraform-deployer` IAM user
- `AWS_REGION` — `us-east-1`
- `EKS_CLUSTER_NAME` — from `terraform output eks_cluster_name`

Static keys (not GitHub OIDC) is the right call here — OIDC needs a new Terraform IAM role/trust-policy resource that doesn't exist yet, and the whole point of doing it is avoiding long-lived secrets in a *permanent* pipeline. This one is torn down same session, so the extra setup isn't worth it today; worth doing as a follow-up exercise later.

---

## Part G — Trigger the pipeline

```
git add 01-auth-service/pom.xml 02-profile-service/pom.xml .gitignore
git rm 03-account-service/maven-debug.log
git commit -m "Add flyway-database-postgresql to auth/profile services; remove stray debug log"
git push origin main
```
Watch `https://github.com/marioazer/banking-app-microservices/actions`: `Build and Test` runs first (6 parallel `./mvnw test -B` jobs, expect all green), then `Deploy to EKS` fires automatically via `workflow_run`. Its `build-and-push` job pushes 6 images to GHCR; its `deploy` job rewrites the image placeholder and runs `kubectl apply -f k8s/`. Note: this step "succeeds" as soon as manifests are applied — it does not wait for pods to actually become healthy, so a green run doesn't yet mean the app works.

Right after this run, do the GHCR visibility step from Part A.4.

---

## Part H — Patch real DB_HOST + secrets (after every CI run)

CI's blanket `kubectl apply -f k8s/` just reset the ConfigMap/Secret back to placeholders — fix them live:

```
kubectl patch configmap banking-global-config -n banking-app \
  --type merge -p "{\"data\":{\"DB_HOST\":\"<real-rds-hostname-no-port>\"}}"

kubectl create secret generic banking-db-secret -n banking-app \
  --from-literal=DB_USERNAME=dbadmin \
  --from-literal=DB_PASSWORD='<same value as terraform.tfvars db_password>' \
  --from-literal=JWT_SECRET_KEY='<any random string>' \
  --from-literal=KYC_WEBHOOK_SECRET='<any random string>' \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl rollout restart deployment -n banking-app --all
```
Kubernetes never live-injects ConfigMap/Secret changes into already-running pods — the restart is required.

Watch: `kubectl get pods -n banking-app -w` — expect all services `Running`, low restart counts. If any pod `CrashLoopBackOff`s, `kubectl logs -n banking-app <pod> --previous` will show either a Flyway error (Part A.1 not applied right) or a datasource error (`DB_HOST` still wrong / RDS security group).

**Likely ingress snag**: `k8s/08-ingress-routes.yaml` uses the older `kubernetes.io/ingress.class: "nginx"` annotation. Recent ingress-nginx chart versions may require `spec.ingressClassName` instead. If `kubectl get ingress -n banking-app` shows no `ADDRESS` after a few minutes, patch it live (no need to edit/commit/re-run CI for this):
```
kubectl patch ingress banking-ingress -n banking-app --type merge -p "{\"spec\":{\"ingressClassName\":\"nginx\"}}"
```

---

## Part I — Verify it actually works

1. `kubectl get pods -n banking-app` — all `Running`.
2. `kubectl get svc -n banking-app ingress-nginx-controller` — grab the NLB hostname.
3. End-to-end request through the real path:
   ```
   curl -i http://<nlb-hostname>/api/v1/auth/login -X POST -H "Content-Type: application/json" -d "{}"
   ```
   Success = HTTP **400/401/422** with a JSON error body (no seeded user yet, so this — not a 200 — is the correct "it works" signal). A 502/504/timeout means something in ingress→service→pod is broken.
4. DB connectivity check (auth/account/transaction don't expose actuator publicly through ingress, so port-forward):
   ```
   kubectl port-forward -n banking-app svc/account-service 8083:8080
   curl http://localhost:8083/actuator/health
   ```
   Expect `{"status":"UP"}`.
5. Kafka wiring check:
   ```
   kubectl logs -n banking-app deploy/notification-service --tail=50
   kubectl logs -n banking-app deploy/audit-service --tail=50
   ```
   Look for consumer-group join logs, no repeated connection-refused errors.
6. Optional: Swagger UI via port-forward — `kubectl port-forward -n banking-app svc/auth-service 8081:8080`, then open `http://localhost:8081/swagger-ui.html`.

---

## Part J — Teardown, in this exact order

This is the step most likely to leave a surprise bill if rushed — do not skip the console confirmations.

1. ```
   helm uninstall ingress-nginx -n banking-app
   ```
2. **Confirm in the AWS console** (EC2 → Load Balancers) that the NLB from Part E is actually gone before proceeding — a leftover Load Balancer is exactly what blocks VPC deletion later and keeps billing regardless of what Terraform thinks.
3. ```
   helm uninstall kafka -n banking-app
   helm uninstall redis -n banking-app
   kubectl delete pvc --all -n banking-app
   kubectl delete namespace banking-app
   ```
   Confirm in EC2 → Volumes that the EBS volumes are gone.
4. Only now:
   ```
   cd terraform
   terraform destroy
   ```
   Expect 10-15+ minutes. If it errors with a VPC "DependencyViolation," something from steps 1-3 wasn't fully cleaned up — check EC2 for a leftover ENI or security group reference, delete it manually, then re-run `terraform destroy`.

---

## Part K — Final verification nothing billable remains

Check directly in console (don't just trust exit codes):
- EC2 → Instances: none running
- EC2 → Load Balancers: empty
- EC2 → Volumes: none leftover
- VPC → NAT Gateways: none remaining
- VPC → Your VPCs: the project VPC gone
- RDS → Databases and Snapshots: both empty
- EKS → Clusters: empty
- IAM → Users: delete or deactivate the `terraform-deployer` access key now
- Billing → Cost Explorer (may take a day to populate): sanity-check the day's spend

---

## Cost estimate (us-east-1, on-demand)

| Scenario | Approx. cost |
|---|---|
| Same-session teardown (~3-4 active hours) | **~$1-2 total** |
| Left running by mistake for a full week | **~$45-55**, compounding every additional week |

The EKS control plane ($0.10/hr) and NAT Gateway (~$0.045/hr) have **no free tier** and bill continuously from creation regardless of use — they're the dominant risk if teardown is delayed, which is why the billing alarm (Part B) and the strict teardown order (Part J) matter most.

---

## Files to be touched when this is executed

- `01-auth-service/pom.xml`, `02-profile-service/pom.xml` — add Flyway dependency
- `.gitignore` — add `maven-debug.log`
- `03-account-service/maven-debug.log` — remove from git
- No other files need committed changes — `k8s/01-namespace-config.yaml`, `k8s/02-secrets.yaml`, and `terraform/*` stay exactly as they are; all AWS-specific values are patched live via `kubectl`/`terraform output`, never committed.

# CI Secrets Checklist

Even though `.github/workflows/test.yml` primarily relies on Gradle + public GitHub Actions, keep this checklist in sync whenever the workflow evolves.

| Secret | Required | Purpose | Config Location |
| ------ | -------- | ------- | --------------- |
| `SLACK_WEBHOOK` | Optional | Enables Slack/Teams notifications for burn-in failures (per `ci-burn-in.md` recommendation). Add notification step before `burn-in` job exits. | Repository → Settings → Secrets and variables → Actions |
| `GH_TOKEN` (default) | Provided automatically | Required for `actions/checkout` + artifact upload. No manual action unless using a PAT. | GitHub-provided |
| `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` (future) | Optional | Only needed if pipeline later pushes images before burn-in. Track in this file when implemented. | Repository secrets |

**Operational Notes**

1. Document any new secret in this file immediately (principle: test governance = documentation).
2. Reference `docs/ci.md` when onboarding engineers so they know how/where to configure secrets.
3. Rotate optional notification/webhook tokens quarterly; store rotation schedule in `docs/retrospectives` if needed.

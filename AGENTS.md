# AGENTS.md - SCHF Core Java

Read before working in this repository:

1. `README.md`
2. `docs/DEVELOPMENT.md`
3. `../schf-workspace/.specs/00_ALWAYS_READ.md`
4. `../schf-workspace/.specs/07_CURRENT_STACK_FREEZE.md`
5. `../schf-workspace/.specs/08_JAVA_V2_DECISION.md`
6. `../schf-workspace/architecture/SCHF_V2_JAVA_ARCHITECTURE.md`

Rules:

- This repo is SCHF v2 Java backend foundation.
- Do not import real data in Sprint 22B.
- Do not touch the frozen PHP/Tauri stack from here.
- Do not commit `.env`, dumps, FDB/FBK files, backups, raw logs, credentials or sensitive screenshots.
- Use Maven for this skeleton.
- Run `mvn verify`, `docker compose config`, `gitleaks detect --redact`, and `git diff --check` before closing a sprint.

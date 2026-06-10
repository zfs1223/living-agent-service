# Access Rules

## Shared folder

- Read: all permitted employees
- Write: enterprise, platform admin, or approved content owner
- Download: disabled in the frontend for normal users

## Department folder

- Read: employees in the same department, enterprise, platform admin
- Write: department manager/editor, enterprise, platform admin
- Cross-department access: denied unless explicitly shared

## Personal folder

- Read/write: owner, enterprise, platform admin
- Cross-access: denied unless explicitly granted

## Archive folder

- Read: as per original scope, but default to read-only
- Write: admins only

## Security notes

- Never trust the frontend alone
- The backend must validate every file operation
- Normalize and reject path traversal attempts (`..`)
- Do not expose raw filesystem download URLs for shared documents unless explicitly allowed

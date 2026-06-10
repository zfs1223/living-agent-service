# Documents Directory Specification

This directory is the host-mounted document source for `living-agent-service`.

## Goals

- Store source documents in a human-readable file hierarchy
- Keep departmental materials easy to browse locally
- Separate physical documents from the knowledge base index/vector store
- Support permissions by department, owner, and shared scope

## Top-level structure

```text
documents/
  shared/
  department/
  personal/
  archive/
  _meta/
```

## Folder meanings

### `shared/`
Company-wide documents that can be viewed by all permitted employees.

Recommended subfolders:

```text
shared/
  company/
  policy/
  handbook/
  templates/
```

Access mode:
- read-only for most users
- write only for enterprise/platform admin or designated document owners
- download disabled in the frontend for shared content

### `department/`
Department-owned documents.

Recommended subfolders:

```text
department/
  hr/
    policies/
    procedures/
    templates/
    records/
  finance/
    policies/
    procedures/
    reports/
    templates/
  tech/
    policies/
    procedures/
    architecture/
    runbooks/
    templates/
  sales/
    policies/
    procedures/
    proposals/
    templates/
  ops/
    policies/
    procedures/
    checklists/
  cs/
    policies/
    procedures/
    playbooks/
  legal/
    policies/
    procedures/
    contracts/
```

Access mode:
- readable by department members
- writable by designated department editors/managers
- other departments denied unless explicitly shared

### `personal/`
Employee-private documents.

Recommended subfolders:

```text
personal/
  <employee_id>/
    notes/
    drafts/
    private/
```

Access mode:
- only the owner, enterprise, or authorized admins can read/write

### `archive/`
Historical documents that are no longer active but must be preserved.

Recommended subfolders:

```text
archive/
  shared/
  department/
  personal/
```

Access mode:
- read-only by default
- write only for administrators

### `_meta/`
Directory metadata and local conventions.

Recommended files:

```text
_meta/
  README.md
  taxonomy.md
  access-rules.md
  naming-conventions.md
```

## Naming conventions

Use stable, sortable names:

- `hr-01-employee-lifecycle.md`
- `finance-02-expense-policy.md`
- `tech-03-deployment-runbook.md`
- `sales-04-offer-template.md`

Suggested pattern:

```text
<department>-<sequence>-<topic>.md
```

or for clearer taxonomy:

```text
<category>-<sequence>-<topic>.md
```

## Document types

- `policies/` for rules, standards, and governance
- `procedures/` for execution steps and approvals
- `templates/` for reusable forms and outlines
- `records/` for meeting notes, audit records, logs
- `runbooks/` for technical operation manuals
- `architecture/` for system design docs
- `drafts/` for work-in-progress documents

## Relation to the knowledge base

The document folder and the knowledge base should not be merged.

- `documents/` = source-of-truth files
- knowledge base = indexed, searchable, semantically organized layer

Recommended workflow:

1. Put the original file in `documents/`
2. Import selected content into the knowledge base
3. Use the knowledge base for intelligent retrieval and cross-document reasoning
4. Use the document folder for exact file reading, audit, and human editing

## Best practice for your product

- Department brains should primarily read the knowledge base for fast semantic access
- Fixed digital employees should use the knowledge base first for reasoning
- The frontend document browser should use `documents/` for browsing and source reading
- Shared folder should remain read-only and download-disabled in the UI

## Suggested repository tree for immediate use

```text
documents/
  README.md
  shared/
    README.md
    company/
      README.md
    policy/
      README.md
    handbook/
      README.md
    templates/
      README.md
  department/
    README.md
    hr/
      README.md
      policies/
        README.md
      procedures/
        README.md
      templates/
        README.md
      records/
        README.md
    finance/
      README.md
      policies/
        README.md
      procedures/
        README.md
      reports/
        README.md
      templates/
        README.md
    tech/
      README.md
      policies/
        README.md
      procedures/
        README.md
      architecture/
        README.md
      runbooks/
        README.md
      templates/
        README.md
    sales/
      README.md
      policies/
        README.md
      procedures/
        README.md
      proposals/
        README.md
      templates/
        README.md
    ops/
      README.md
      policies/
        README.md
      procedures/
        README.md
      checklists/
        README.md
    cs/
      README.md
      policies/
        README.md
      procedures/
        README.md
      playbooks/
        README.md
    legal/
      README.md
      policies/
        README.md
      procedures/
        README.md
      contracts/
        README.md
  personal/
    README.md
  archive/
    README.md
  _meta/
    README.md
    taxonomy.md
    access-rules.md
    naming-conventions.md
```

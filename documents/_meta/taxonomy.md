# Document Taxonomy

## Purpose

This file defines the canonical folder taxonomy for the `documents/` mount.

## Canonical root folders

- `shared/` — company-wide documents
- `department/` — department-owned documents
- `personal/` — employee-private documents
- `archive/` — archived documents
- `_meta/` — conventions and governance

## Recommended department groups

- `hr/`
- `finance/`
- `tech/`
- `sales/`
- `ops/`
- `cs/`
- `legal/`

## Recommended document types

- `policies/`
- `procedures/`
- `templates/`
- `records/`
- `reports/`
- `architecture/`
- `runbooks/`
- `proposals/`
- `checklists/`
- `playbooks/`
- `contracts/`

## Metadata recommendation

Consider storing a small YAML front matter block at the top of each markdown document:

```yaml
scope: department
department: hr
type: policy
owner: hr
visibility: internal
linked_knowledge: true
```

## Linking to the knowledge base

A document should be imported into the knowledge base when it is:

- frequently queried by agents
- useful for cross-document reasoning
- likely to be referenced semantically rather than verbatim
- stable enough to index as knowledge

Source-of-truth documents remain in `documents/`.

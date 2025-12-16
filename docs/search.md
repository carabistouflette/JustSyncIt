# Search Syntax Guide

JustSyncIt uses SQLite's FTS5 for full-text search. This allows for powerful queries to find your files efficiently.

## Basic Search

Simply type terms to find files containing them in their path.
```bash
justsyncit search "report"
justsyncit search "java src"
```
*Note: A space between terms acts as an implicit AND operator. Files must contain BOTH terms.*

## Boolean Operators

You can use `OR` and `NOT` to refine your search.

### OR
Find files matching *any* of the terms.
```bash
# Finds files with "report" OR "presentation"
justsyncit search "report OR presentation"
```

### NOT
Exclude files containing specific terms.
```bash
# Finds files with "project" but NOT "draft"
justsyncit search "project NOT draft"
```

## Wildcards

Use the `*` asterisk for prefix matching.
```bash
# Finds "document", "documents", "documentation"
justsyncit search "doc*"
```

## Phrases

Use quotes to search for an exact phrase.
```bash
# Finds "/Project Alpha/report.txt" but not "/Project/Alpha/report.txt"
justsyncit search "\"Project Alpha\""
```

## Proximity

Use `NEAR()` to find terms close to each other.
```bash
# Finds "error" near "log" (default distance is 10 tokens)
justsyncit search "NEAR(error log)"
```

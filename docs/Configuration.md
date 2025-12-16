# Configuration Guide

JustSyncIt can be configured through a YAML configuration file, environment variables, or command-line flags.

## Configuration File

By default, JustSyncIt looks for a configuration file at `~/.justsyncit/config.yaml`.

### Structure

```yaml
backup:
  default_chunk_size: 65536     # 64KB (Default)
  verify_integrity: true        # Verify hashes after write
  include_hidden: false         # Skip hidden files by default

server:
  port: 8080                    # Default server port
  transport: TCP                # TCP or QUIC
  max_connections: 100

storage:
  path: ~/.justsyncit/storage   # Location of the content store
  gc_enabled: true              # Enable automatic garbage collection
```

## Environment Variables

Environment variables take precedence over the configuration file but are overridden by CLI flags.

| Variable | Description | Default |
|----------|-------------|---------|
| `JUSTSYNCIT_HOME` | Base directory for data and config | `~/.justsyncit` |
| `JUSTSYNCIT_LOG_LEVEL` | Logging verbosity | `INFO` |
| `JUSTSYNCIT_SERVER_PORT` | Default port for server | `8080` |
| `JUSTSYNCIT_CHUNK_SIZE` | Target average chunk size (bytes) | `65536` |

## Java Options

For performance tuning, you can pass standard Java options:

```bash
# Example: limit memory to 4GB
java -Xmx4g -jar justsyncit.jar ...
```

# Zero-Knowledge Encryption Architecture

JustSyncIt implements a "Zero-Knowledge" privacy architecture, ensuring that the storage backend (server) never possesses the keys required to decrypt user data. This guarantees that your data remains private, even in the event of a server compromise.

## Core Concepts

### 1. Client-Side Encryption
All data is encrypted on the client device **before** it is transmitted to the server or written to disk.
- **Algorithm**: AES-256-GCM (Authenticated Encryption).
- **Key Management**: Keys are derived from a user secret and never leave the client.

### 2. Deduplication-Friendly Encryption
To maintain storage efficiency, we use a form of **Convergent Encryption**:
- **Data Chunks**: Encrypted deterministically. Identical plaintext chunks encrypt to identical ciphertext blobs (for the same user).
  - *Key*: User Master Key
  - *IV*: Derived from the SHA-256 hash of the *plaintext* chunk.
- **Security Check**: This allows local deduplication (storing the same file twice doesn't consume double space) without leaking plaintext information to the server beyond the fact that "User has chunk X".

### 3. Encrypted Metadata
Metadata (filenames, paths, timestamps) is sensitive.
- **Implementation**: The local SQLite database stores file paths in an encrypted format.
- **Mode**: Non-deterministic AES-256-GCM (Random IVs) to ensure that two files with the same name in different directories produce different ciphertexts.

### 4. Secure Search (Blind Indexing)
To enable searching without decrypting the entire database:
- **Blind Indices**: We generate "search tokens" by hashing keywords with a dedicated search key (HMAC-SHA256).
- **Process**:
  1. Client types "document".
  2. Client computes `HMAC(SearchKey, "document")`.
  3. Client queries database for this hash.
  4. Database returns basic file IDs.
  5. Client decrypts the actual file paths locally for display.

## Architecture Diagram

```mermaid
graph TD
    User[User / Client] -->|Plaintext| BackupService
    BackupService -->|Plaintext| EncryptedContentStore
    
    subgraph "Encryption Layer"
    EncryptedContentStore -->|Plaintext| EncryptionService [AesGcmEncryptionService]
    EncryptionService -->|Ciphertext| FilesystemContentStore
    end
    
    subgraph "Metadata Layer"
    BackupService -->|Metadata| SqliteMetadataService
    SqliteMetadataService -->|Path| EncryptionService
    SqliteMetadataService -->|Keywords| BlindIndexSearch
    BlindIndexSearch -->|HMAC Tokens| DB[(SQLite DB)]
    end
    
    FilesystemContentStore -->|Encrypted Blobs| Disk[(Storage Disk)]
    DB -->|Encrypted Paths & Tokens| Disk
```

## Key Recovery
JustSyncIt provides a mechanism to back up your encryption keys securely.
- **Recovery Code**: A human-readable code (e.g., `A3F1-92BCA-...`) that can reconstruct your Master Key.
- **Process**:
  - The `KeyRecoveryService` exports the key.
  - **IMPORTANT**: If you lose both your password and this recovery code, your data is **permanently unrecoverable**.

## Technical Implementation Details

| Component | Responsibility | Encryption Mode |
|-----------|----------------|-----------------|
| `EncryptedContentStore` | Decorates storage, handles chunk I/O | Deterministic AES-GCM (IV=Hash(P)) |
| `SqliteMetadataService` | Manages file table, stores paths | Randomized AES-GCM (Random IV) |
| `BlindIndexSearch` | Tokenizes and hashes keywords | HMAC-SHA256 |
| `AesGcmEncryptionService` | Core crypto primitives | AES/GCM/NoPadding |

## Future Improvements
- **Encrypted Backup Verification**: Tools to verify the integrity of the encrypted store without the key (using checksums of ciphertexts).

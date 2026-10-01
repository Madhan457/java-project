# SecureVault - Secure File Encryption & Decryption System

SecureVault is a local Java web application for storing encrypted copies of files. Users sign in with an account password, choose a separate password for each file, and download either the encrypted copy or a temporary decrypted copy.

    mvnw
    mvnw.cmd
## Features
    run-local.ps1

      SecureVaultApplication.java
      controller/
        ApiExceptionHandler.java
        AuthController.java
        FileController.java
      dto/
        ApiError.java
        AuthResponse.java
        FileMetadataResponse.java
        LoginRequest.java
        RegisterRequest.java
      entity/
        FileMetadata.java
        User.java
      repository/
        FileMetadataRepository.java
        UserRepository.java
      security/SecurityConfig.java
      service/
        AuthService.java
        EncryptionService.java
        FileService.java
      util/FileUtil.java
    src/main/resources/application.properties
    src/test/java/com/securevault/service/EncryptionServiceTest.java
## Technologies

- Java 25, Spring Boot 3.5, Spring Web, Spring Data JPA, Spring Security, Bean Validation
- Maven and MySQL 8+
- HTML, CSS, and vanilla JavaScript using Fetch API
    js/login.js
    js/register.js
    js/dashboard.js
  storage/encrypted/     Created automatically when the backend starts
## Security mechanisms

Account passwords are hashed with BCrypt. File-encryption passwords are never stored. A fresh random salt and IV are created for every encryption; the derived AES key exists only while processing the request. The salt and IV are non-secret metadata in MySQL. AES-GCM authenticates the ciphertext, so a wrong password or modified encrypted file fails verification.

Encrypted files are written to `storage/encrypted/` under a generated UUID-based filename. Original filenames are metadata only and are never used to resolve a storage path. API responses do not include filesystem paths or encryption metadata. Before a decrypted download starts, the application writes the authenticated result to a temporary file and then streams it to the response; it removes that file when streaming ends. A process or machine crash can leave an operating-system temporary file behind, so this educational implementation should not be used for highly sensitive production data.

The frontend uses `http://localhost:5500` and sends the session cookie to `http://localhost:8080`. CORS permits `http://localhost:5500` and `http://127.0.0.1:5500`. This setup is intended for local learning, not deployment on an untrusted network. Production use requires HTTPS, CSRF protection appropriate to the deployment, operational secret management, storage quotas, and a reviewed key-recovery policy.

## Project structure

```text
SecureVault/
  backend/
    pom.xml
    src/main/java/com/securevault/
      controller/       REST endpoints and error responses
      dto/              Request and response records
      entity/           User and file metadata JPA entities
      repository/       Spring Data repositories
      security/         Session, CORS, and BCrypt configuration
      service/          Authentication, file handling, and encryption
      util/             Filename and storage-path checks
    src/main/resources/application.properties
    src/test/java/      Encryption tests
  frontend/
    index.html
    register.html
    dashboard.html
    css/style.css
    js/
  storage/encrypted/
```

## MySQL setup

Install MySQL Server 8 or newer and start its Windows service. On the first connection, Connector/J creates the `securevault` database if it does not exist; the configured MySQL root account must have permission to create databases. Hibernate creates or updates the `users` and `file_metadata` tables on startup. No manual SQL is required. `DB_PASSWORD` is read from the process environment and is never stored in this project.

## Application configuration

`backend/src/main/resources/application.properties` uses these defaults and environment-variable overrides:

| Setting | Default | Environment variable |
| --- | --- | --- |
| JDBC URL | `jdbc:mysql://localhost:3306/securevault?createDatabaseIfNotExist=true` | `DB_URL` |
| Database user | `root` | `DB_USERNAME` |
| Database password | Required, no source default | `DB_PASSWORD` |
| HTTP port | `8080` | `SERVER_PORT` |
| Encrypted storage | `../storage/encrypted` from the backend working directory | `SECUREVAULT_STORAGE_DIR` |
| PBKDF2 iterations | `600000` | `SECUREVAULT_PBKDF2_ITERATIONS` |

The application fails at startup if `DB_PASSWORD` is missing. Do not put an actual database password in source control. The entity mapping uses `users.username`, `users.email` (150 characters), and `users.password` to match the supplied schema. Hibernate adds the `encryption_algorithm`, `pbkdf2_iterations`, `salt`, and `iv` metadata columns to `file_metadata`, which are required to decrypt each file; encrypted file contents remain on disk only. The iteration count is recorded per file so changing the configured default does not prevent decrypting existing uploads.

## Run the backend

Install a Java 25 JDK and start MySQL. From PowerShell, the launcher detects the MySQL service and prompts for the root password without echoing it:

```powershell
cd SecureVault/backend
.\run-local.ps1
```

The launcher uses the included Maven wrapper. Connector/J creates `securevault` on first connection if needed, and Hibernate creates/updates the tables; if you have already created the supplied tables, Hibernate adds only the encryption metadata columns required by this application. The storage directory is also created at application startup. The API will be available at `http://localhost:8080`. From the backend directory, the default encrypted-file path resolves to `SecureVault/storage/encrypted/`.

## Run the frontend

Serve the frontend at the exact allowed local origin, `http://localhost:5500`. One option is the VS Code Live Server extension: set its host to `localhost` and port to `5500`, then open `frontend/index.html`. Alternatively, from the `SecureVault` directory with Python installed:

```powershell
py -m http.server 5500 --directory frontend
```

Open `http://localhost:5500`. Do not open the HTML as a `file://` URL; browser session cookies and CORS require the local HTTP origin.

## API endpoints

All endpoints use `http://localhost:8080`. Authenticated routes use the `JSESSIONID` session cookie. The browser frontend includes credentials in its requests.

| Method | Path | Authentication | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | No | JSON body: `name`, `email`, `password` |
| `POST` | `/api/auth/login` | No | JSON body: `email`, `password`; starts a session |
| `GET` | `/api/auth/me` | Yes | Return the current account name and email |
| `POST` | `/api/auth/logout` | Yes | Invalidate the current session |
| `POST` | `/api/files/encrypt` | Yes | Multipart fields: `file`, `password` |
| `GET` | `/api/files` | Yes | List only the current user's file metadata |
| `GET` | `/api/files/{id}/download` | Yes | Download the encrypted `.enc` file |
| `POST` | `/api/files/{id}/decrypt` | Yes | Form field: `password`; download authenticated plaintext |
| `DELETE` | `/api/files/{id}` | Yes | Delete the encrypted file and its metadata |

Registration and encryption passwords must be at least 10 characters; BCrypt-compatible passwords are limited to 72 UTF-8 bytes. Uploaded files may be up to 100 MB. Errors are JSON with a user-facing `message`; invalid credentials return 401, inaccessible files return 404, and a wrong decryption password or failed GCM check returns 422.

## Encryption workflow

1. The authenticated user selects a file and enters a file-specific encryption password.
2. The backend streams the multipart file through `AES/GCM/NoPadding` using an AES-256 key derived from the password, random salt, and PBKDF2-HMAC-SHA256.
3. It saves the ciphertext and GCM tag under a UUID-based `.enc` name, then stores the original filename, size, algorithm, salt, IV, upload time, and owner in MySQL.
4. The frontend receives metadata without a filesystem path or secret key.

## Decryption workflow

1. The owner submits the per-file password for a file they own.
2. The backend loads the ciphertext and metadata, derives the AES key again, and decrypts to a temporary file.
3. The GCM tag is verified while the entire file is read. An incorrect password or tampering fails before the download response begins.
4. The authenticated plaintext is streamed as a download, and the temporary file is deleted after the response completes.

## Example usage

1. Start MySQL and create `securevault`.
2. Start the backend and frontend using the commands above.
3. Visit `http://localhost:5500/register.html`, create an account, then sign in.
4. In **Encrypt a file**, select a file and enter a new file password of at least 10 characters.
5. Use **Download** to save its encrypted copy, or **Decrypt** and enter the same file password to recover the original.
6. Use **Delete** to remove the encrypted copy and its database metadata.

The account password is used only to sign in. It does not decrypt files. Losing a file password means the encrypted file cannot be recovered.

## Limitations

- Designed for one local Spring Boot instance and educational use; it has no multi-server storage, backups, account recovery, rate limiting, or administrator interface.
- The 100 MB limit and a temporary plaintext file during verified decryption constrain larger-file workflows.
- Session-based authentication is configured for the local origin only and the API disables CSRF for this local browser demo.
- Database schema management uses Hibernate `ddl-auto=update`; production systems should use versioned migrations.
- Deleted files are removed through the filesystem API but are not securely erased from disk or backups.

## Future improvements

- Add versioned database migrations, audit events, login throttling, and configurable quotas.
- Use a production-grade deployment with HTTPS, restrictive cookie settings, CSRF protection, monitoring, and regularly tested backups.
- Consider chunked encryption and authenticated streaming for files larger than the educational upload limit.
- Add integration tests against a disposable MySQL instance and browser-level tests for the complete user workflow.
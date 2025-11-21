# Container Scan Notice

Container image scanning is skipped in this automated scan.
To scan the container manually:

1. Build the image: docker build -t justsyncit:0.1.0 .
2. Scan with Trivy: trivy image justsyncit:0.1.0

The Dockerfile is optimized for security with minimal base image.

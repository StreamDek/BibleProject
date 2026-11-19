# Deploying the backend to the VPS

The VPS runs only the **API (Ktor), PostgreSQL and Caddy** (and, from Phase 2, the admin site).
Bible text, story content and images are static files on **Cloudflare R2 behind the Cloudflare CDN**, so
reading never depends on this server and its 2 TB/month bandwidth is not spent on images.

## Sizing (current VPS: 2 GB RAM)

| Service | Memory limit |
|---|---|
| PostgreSQL 16 (tuned: 128 MB shared buffers, 30 connections) | 512 MB |
| API (JVM, `-Xmx256m`) | 400 MB |
| Caddy | 128 MB |
| Admin site (Phase 2) | about 256 MB |
| OS, Docker, headroom | the rest |

This is comfortable up to roughly 10k-30k monthly users. Image processing (AVIF encoding) and pack builds
are **not** run on the VPS; they run in GitHub Actions or on a developer machine and upload to R2.

## One-time setup

```bash
# 1. Swap (protects against memory spikes on a 2 GB machine)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 2. Firewall: only SSH and HTTPS
sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw allow 443/udp && sudo ufw enable

# 3. Automatic security updates, SSH keys only (set PasswordAuthentication no in /etc/ssh/sshd_config)
sudo apt install -y unattended-upgrades fail2ban rclone

# 4. Docker (https://docs.docker.com/engine/install/)

# 5. App
sudo mkdir -p /opt/bible && sudo chown $USER /opt/bible
git clone https://github.com/StreamDek/BibleProject.git /opt/bible
cd /opt/bible/deploy && cp .env.example .env && nano .env
docker compose up -d
curl https://$API_DOMAIN/health
```

## DNS and CDN (Cloudflare)

- `api.<domain>` → the VPS IP, proxied (orange cloud). SSL mode "Full (strict)".
- `cdn.<domain>` → the R2 bucket's custom domain, with a cache rule "Cache everything" (files are
  content-addressed and immutable).
- `admin.<domain>` → the VPS, proxied, ideally behind Cloudflare Access (Phase 2).

## Backups

`scripts/backup.sh` dumps PostgreSQL nightly to an R2 bucket (install the cron line in the script header).
Once a month, test a restore into a scratch database:

```bash
scripts/restore.sh bible-<timestamp>.dump     # restores into bible_restore_test
```

## Updating

CI builds and publishes the API image on every push to `main`. On the VPS:

```bash
cd /opt/bible/deploy && git pull && docker compose pull && docker compose up -d
```

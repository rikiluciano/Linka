#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_dir"

if [[ "$(git branch --show-current)" != "main" ]]; then
  echo "Error: sync-to-github.sh solo publica desde la rama main." >&2
  exit 1
fi

export GIT_SSH_COMMAND="ssh -i ${HOME}/.ssh/linka_github -o IdentitiesOnly=yes"

version="${2:-}"
if [[ -n "$version" ]]; then
  if [[ ! "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: la versión debe tener formato vMAJOR.MINOR.PATCH (por ejemplo v1.0.0)." >&2
    exit 1
  fi
  if git ls-remote --exit-code --tags origin "refs/tags/$version" >/dev/null 2>&1; then
    echo "Error: la etiqueta $version ya existe en GitHub." >&2
    exit 1
  fi
fi

git pull --rebase --autostash origin main
git add --all

if ! git diff --cached --quiet; then
  message="${1:-Sincronizar cambios de Linka desde el servidor}"
  git commit -m "$message"
fi

git push origin main

if [[ -n "${2:-}" ]]; then
  git tag -a "$version" -m "Linka $version"
  git push origin "$version"
  echo "Etiqueta $version publicada; GitHub Actions compilará y adjuntará linka.apk al Release."
fi

echo "Sincronización con GitHub completada."

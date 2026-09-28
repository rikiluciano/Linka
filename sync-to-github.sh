#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_dir"

if [[ "$(git branch --show-current)" != "main" ]]; then
  echo "Error: la publicación debe partir de la rama main." >&2
  exit 1
fi

export GIT_SSH_COMMAND="ssh -i ${HOME}/.ssh/linka_github -o IdentitiesOnly=yes"

if [[ "${1:-}" == "--message-base64" && -n "${2:-}" && "${3:-}" == "--release" ]]; then
  message="$(printf '%s' "$2" | base64 --decode)"
elif [[ "${2:-}" == "--release" ]]; then
  message="${1:-Publicar cambios de Linka}"
else
  echo "Uso: sync-to-github.sh 'mensaje de commit' --release" >&2
  exit 2
fi

git pull --rebase --autostash origin main
git fetch --tags origin
git add --all

if git diff --cached --quiet; then
  ahead="$(git rev-list --count origin/main..HEAD)"
  pending_tag=""
  for tag in $(git tag --list 'v[0-9]*' --sort=-version:refname); do
    if ! git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then
      pending_tag="$tag"
      break
    fi
  done
  if (( ahead > 0 )) && [[ -n "$pending_tag" ]]; then
    git push --atomic origin main "refs/tags/$pending_tag"
    echo "Se completó la publicación pendiente de main y $pending_tag."
    exit 0
  elif (( ahead == 0 )) && [[ -n "$pending_tag" ]]; then
    git push --atomic origin "refs/tags/$pending_tag"
    echo "Se completó la publicación pendiente de $pending_tag."
    exit 0
  elif (( ahead > 0 )); then
    echo "Error: hay commits locales pendientes sin una etiqueta de versión." >&2
    exit 1
  fi
  echo "No hay cambios para publicar; no se creó una versión nueva."
  exit 0
fi

latest_tag="$(git tag --list 'v[0-9]*' --sort=-version:refname | head -n 1)"
if [[ -z "$latest_tag" ]]; then
  current_version="$(sed -n "s/.*versionName ['\"]\([^'\"]*\)['\"].*/\1/p" app/build.gradle | head -n 1)"
else
  current_version="${latest_tag#v}"
fi

if [[ ! "$current_version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Error: no se pudo leer una versión MAJOR.MINOR.PATCH válida." >&2
  exit 1
fi
next_version="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$((BASH_REMATCH[3] + 1))"

python3 - "$next_version" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path("app/build.gradle")
source = path.read_text(encoding="utf-8")
version_code = re.search(r"(?m)^(\s*versionCode\s+)(\d+)\s*$", source)
version_name = re.search(r"(?m)^(\s*versionName\s+)[\"'][^\"']+[\"']\s*$", source)
if not version_code or not version_name:
    raise SystemExit("app/build.gradle debe declarar versionCode y versionName explícitos.")
source = re.sub(r"(?m)^(\s*versionCode\s+)\d+(\s*)$", lambda match: f"{match.group(1)}{int(version_code.group(2)) + 1}{match.group(2)}", source, count=1)
source = re.sub(r"(?m)^(\s*versionName\s+)[\"'][^\"']+[\"'](\s*)$", lambda match: f"{match.group(1)}'{sys.argv[1]}'{match.group(2)}", source, count=1)
path.write_text(source, encoding="utf-8")
PY

git add app/build.gradle
git commit -m "$message (v$next_version)"
git tag -a "v$next_version" -m "Linka v$next_version"
git push --atomic origin main "refs/tags/v$next_version"

echo "Cambios publicados en main. La etiqueta v$next_version activó la compilación y el Release con linka.apk."

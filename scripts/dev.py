#!/usr/bin/env python3
"""Project-owned local databases. Python standard library only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


class OperationError(Exception):
    pass


class Controller:
    def __init__(self, root):
        self.root = Path(root).resolve()
        self.local = self.root / '.local'
        self.owner = hashlib.sha256(str(self.root).encode()).hexdigest()[:12]

    def key(self, mode, name=None):
        if mode not in ('single', 'sharding'):
            raise OperationError('mode must be single or sharding')
        name = name or mode
        if not re.fullmatch(re.escape(mode) + r'(-restore-[0-9a-f]{8})?', name):
            raise OperationError('invalid local environment name')
        return name

    def private_dir(self):
        if self.local.is_symlink():
            raise OperationError('local state directory must not be a symlink')
        self.local.mkdir(mode=0o700, exist_ok=True)
        self.local.chmod(0o700)

    def create(self, mode, name=None, port=None):
        key = self.key(mode, name)
        state_file = self.local / f'state-{key}.json'
        if state_file.exists() or state_file.is_symlink():
            return self.load(mode, key)
        self.private_dir()
        port = port or (5432 if mode == 'single' else 55432)
        env_file = self.local / f'db-{key}.env'
        config = {
            'APP_DB_MODE': mode,
            'APP_DB_PORT': str(port),
            'APP_DB_JDBC_URL': f'jdbc:postgresql://127.0.0.1:{port}/starter',
            'APP_DB_USERNAME': 'app_runtime',
            'APP_DB_PASSWORD': secrets.token_urlsafe(24),
            'APP_DB_MIGRATION_USERNAME': 'app_migration',
            'APP_DB_MIGRATION_PASSWORD': secrets.token_urlsafe(24),
            'APP_DB_ADMIN_PASSWORD': secrets.token_urlsafe(24),
        }
        with os.fdopen(os.open(env_file, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), 'w') as stream:
            stream.write(''.join(f'{key}={value}\n' for key, value in config.items()))
        state = {'owner': str(self.root), 'mode': mode, 'name': key,
                 'project': f'starter-{self.owner}-{key}', 'port': port,
                 'env_file': str(env_file)}
        with os.fdopen(os.open(state_file, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), 'w') as stream:
            json.dump(state, stream)
        return state

    def load(self, mode, name=None):
        key = self.key(mode, name)
        if self.local.is_symlink():
            raise OperationError('local state directory must not be a symlink')
        state_file = self.local / f'state-{key}.json'
        if state_file.is_symlink():
            raise OperationError('state must not be a symlink')
        state = json.loads(state_file.read_text())
        expected_env = self.local / f'db-{key}.env'
        if (state.get('owner') != str(self.root) or state.get('mode') != mode
                or state.get('name') != key or state.get('project') != f'starter-{self.owner}-{key}'
                or state.get('env_file') != str(expected_env) or expected_env.is_symlink()):
            raise OperationError('refusing resources not owned by this project')
        config = self.config(state)
        expected_url = f"jdbc:postgresql://127.0.0.1:{state['port']}/starter"
        if config.get('APP_DB_JDBC_URL') != expected_url:
            raise OperationError('refusing a non-local database URL')
        external = os.environ.get('APP_DB_JDBC_URL')
        if external and external != expected_url:
            raise OperationError('external database variables are set; local operation refused')
        return state

    def config(self, state):
        return dict(line.split('=', 1) for line in Path(state['env_file']).read_text().splitlines() if line and not line.startswith('#'))

    def environment(self, state):
        return dict(os.environ, **self.config(state))

    def compose(self, state):
        folder = 'postgres' if state['mode'] == 'single' else 'citus'
        return ['docker', 'compose', '--env-file', state['env_file'], '-p', state['project'],
                '-f', str(self.root / 'infra' / folder / 'compose.yaml')]

    def run(self, command, **kwargs):
        return subprocess.run(command, cwd=self.root, check=True, **kwargs)

    def compose_run(self, state, arguments, **kwargs):
        # Compose gives shell variables precedence over --env-file. Always
        # replace APP_DB_* with the selected target's private configuration.
        return self.run(self.compose(state) + arguments, env=self.environment(state), **kwargs)

    def migrate(self, state):
        self.run([str(self.root / 'gradlew'), ':core:migrate'], env=self.environment(state))

    def up(self, state, migrate=True):
        self.compose_run(state, ['up', '-d', '--wait', '--wait-timeout', '90'])
        if state['mode'] == 'sharding':
            self.compose_run(state, ['exec', '-T', 'coordinator', 'psql', '-U', 'bootstrap', '-d', 'starter',
                                           '-v', 'ON_ERROR_STOP=1', '-f', '/opt/starter/topology.sql'])
        if migrate:
            self.migrate(state)

    def transfer(self, state, operation, path):
        self.run([str(self.root / 'gradlew'), f':core:sample{operation}', f'-PdataFile={path.resolve()}'],
                 env=self.environment(state))

    def export(self, state, path):
        if path.exists():
            raise OperationError('export target already exists')
        if state['mode'] == 'single':
            with os.fdopen(os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), 'wb') as stream:
                try:
                    self.compose_run(state, ['exec', '-T', 'postgres', 'pg_dump', '-U', 'app_migration',
                                                   '-d', 'starter', '--no-owner', '--format=custom'], stdout=stream)
                except Exception:
                    path.unlink(missing_ok=True)
                    raise
        else:
            self.transfer(state, 'Export', path)

    def restore(self, mode, path):
        if not path.is_file():
            raise OperationError('restore input does not exist')
        name = f'{mode}-restore-{secrets.token_hex(4)}'
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            port = listener.getsockname()[1]
        state = self.create(mode, name, port)
        print(f"New restore target: {name}; configuration: {state['env_file']}", flush=True)
        self.up(state, migrate=(mode == 'sharding'))
        if mode == 'single':
            with path.open('rb') as stream:
                self.compose_run(state, ['exec', '-T', 'postgres', 'pg_restore', '-U', 'app_migration',
                                               '-d', 'starter', '--clean', '--if-exists', '--no-owner', '--exit-on-error'], stdin=stream)
            self.migrate(state)
        else:
            self.transfer(state, 'Import', path)
        return state

    def java_path(self):
        result = self.run([str(self.root / 'gradlew'), '-q', ':core:javaPath'], capture_output=True, text=True)
        for line in result.stdout.splitlines():
            if line.startswith('STARTER_JAVA_PATH='):
                return line.split('=', 1)[1]
        raise OperationError('Gradle did not return its managed Java launcher')


def doctor(root):
    missing = []
    for tool in ('mise', 'git', 'docker', 'jq', 'rg', 'ast-grep', 'uv', 'python3'):
        if shutil.which(tool) is None:
            missing.append(tool)
            print(f'Missing {tool}: run mise install; Git uses mise bootstrap packages apply; Docker needs its daemon installed.')
    if not (root / 'gradlew').is_file():
        print('Missing Gradle Wrapper in project directory')
        missing.append('gradlew')
    if not missing:
        try:
            subprocess.run(['docker', 'info', '--format', '{{.ServerVersion}}'], check=True, capture_output=True)
            subprocess.run(['docker', 'compose', 'version'], check=True, capture_output=True)
        except subprocess.CalledProcessError:
            print('Docker daemon or Compose unavailable; start Docker and verify access.')
            return 1
        print('Tools and Docker ready. Project JDK: Gradle-managed Temurin 25; run ./gradlew javaToolchains.')
    return int(bool(missing))


def main(argv=None, *, root=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    extra = []
    if '--' in argv:
        separator = argv.index('--')
        argv, extra = argv[:separator], argv[separator + 1:]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('doctor', 'up', 'down', 'reset', 'migrate', 'export', 'restore', 'dev', 'cli', 'serena'))
    parser.add_argument('--mode', choices=('single', 'sharding'), default='single')
    parser.add_argument('--name')
    parser.add_argument('--confirm-local-reset', action='store_true')
    parser.add_argument('--input', type=Path)
    parser.add_argument('--output', type=Path)
    try:
        args = parser.parse_args(argv)
        controller = Controller(root or ROOT)
        if args.command == 'doctor':
            return doctor(controller.root)
        if args.command == 'serena':
            launcher = controller.java_path()
            env = dict(os.environ, JAVA_HOME=str(Path(launcher).parent.parent))
            controller.run(['serena', 'start-mcp-server', '--project-from-cwd', '--open-web-dashboard', 'false'] + extra, env=env)
            return 0
        controller.key(args.mode, args.name)
        if args.command == 'reset' and not args.confirm_local_reset:
            raise OperationError('reset requires --confirm-local-reset; it deletes only the selected local volumes')
        if args.command == 'restore':
            if args.input is None:
                raise OperationError('restore requires --input')
            if args.name:
                raise OperationError('restore always creates a new target; do not supply --name')
            controller.restore(args.mode, args.input.resolve())
            return 0
        state = controller.create(args.mode, args.name) if args.command == 'up' else controller.load(args.mode, args.name)
        # Recheck every action against state and local URL, including freshly created state.
        state = controller.load(args.mode, state['name'])
        if args.command == 'up':
            controller.up(state)
        elif args.command in ('down', 'reset'):
            controller.compose_run(state, ['down'] + (['--volumes'] if args.command == 'reset' else []))
        elif args.command == 'migrate':
            controller.migrate(state)
        elif args.command == 'export':
            if args.output is None:
                raise OperationError('export requires --output')
            controller.export(state, args.output.resolve())
        elif args.command == 'dev':
            controller.run([str(controller.root / 'gradlew'), ':server:quarkusDev'] + extra, env=controller.environment(state))
        elif args.command == 'cli':
            controller.run([str(controller.root / 'gradlew'), ':cli:quarkusBuild'], stdout=sys.stderr)
            controller.run([controller.java_path(), '-jar', str(controller.root / 'cli/build/starter-cli-runner.jar')] + extra,
                           env=controller.environment(state))
        return 0
    except SystemExit as error:
        return int(error.code)
    except subprocess.CalledProcessError as error:
        print(f'Operation failed (exit {error.returncode}); source configuration retained.', file=sys.stderr)
        return error.returncode if 0 < error.returncode < 256 else 1
    except (OperationError, OSError, ValueError, KeyError) as error:
        # Never echo arbitrary configuration or subprocess output containing credentials.
        message = str(error) if isinstance(error, OperationError) else 'Cannot read local state or execute required tool'
        print(message, file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == '__main__':
    sys.exit(main())

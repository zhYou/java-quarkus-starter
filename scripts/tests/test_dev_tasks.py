import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('starter_dev', Path(__file__).parents[1] / 'dev.py')
dev = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(dev)


class DevBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.controller = dev.Controller(self.root)

    def test_reset_requires_confirmation_and_rejects_unknown_or_external(self):
        with patch.object(dev.subprocess, 'run') as run:
            self.assertNotEqual(0, dev.main(['reset'], root=self.root))
            self.assertNotEqual(0, dev.main(['reset', '--mode', 'unknown', '--confirm-local-reset'], root=self.root))
            self.controller.create('single')
            with patch.dict(dev.os.environ, {'APP_DB_JDBC_URL': 'jdbc:postgresql://prod.example/starter'}):
                self.assertNotEqual(0, dev.main(['reset', '--confirm-local-reset'], root=self.root))
            run.assert_not_called()

    def test_down_retains_volumes_and_only_uses_owned_project(self):
        state = self.controller.create('single')
        with patch.object(dev.subprocess, 'run', return_value=dev.subprocess.CompletedProcess([], 0)) as run:
            self.assertEqual(0, dev.main(['down'], root=self.root))
            command = run.call_args.args[0]
            self.assertIn(state['project'], command)
            self.assertIn('down', command)
            self.assertNotIn('--volumes', command)
            self.assertNotIn('prune', command)
        state_path = self.root / '.local' / 'state-single.json'
        state_path.write_text(state_path.read_text().replace(state['project'], 'foreign-project'))
        with patch.object(dev.subprocess, 'run') as run:
            self.assertNotEqual(0, dev.main(['reset', '--confirm-local-reset'], root=self.root))
            run.assert_not_called()

    def test_credentials_private_and_not_replaced(self):
        state = self.controller.create('sharding')
        path = Path(state['env_file'])
        before = path.read_bytes()
        self.assertEqual(0o600, path.stat().st_mode & 0o777)
        self.assertEqual(state, self.controller.create('sharding'))
        self.assertEqual(before, path.read_bytes())

    def test_doctor_names_missing_tools_without_reading_secrets(self):
        output = io.StringIO()
        with patch.object(dev.shutil, 'which', return_value=None), contextlib.redirect_stdout(output):
            self.assertNotEqual(0, dev.main(['doctor'], root=self.root))
        self.assertIn('docker', output.getvalue())
        self.assertIn('mise', output.getvalue())

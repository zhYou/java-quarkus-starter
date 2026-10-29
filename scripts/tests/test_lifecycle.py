from test_dev_tasks import dev
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


class LifecycleBoundaryTest(unittest.TestCase):
    def test_confirmed_reset_targets_one_owned_project(self):
        for mode in ('single', 'sharding'):
            with tempfile.TemporaryDirectory() as root:
                state = dev.Controller(root).create(mode)
                with patch.object(dev.subprocess, 'run', return_value=dev.subprocess.CompletedProcess([], 0)) as run:
                    self.assertEqual(0, dev.main(['reset', '--mode', mode, '--confirm-local-reset'], root=Path(root)))
                    argv = run.call_args.args[0]
                    self.assertEqual(['down', '--volumes'], argv[-2:])
                    self.assertIn(state['project'], argv)
                    self.assertNotIn('prune', argv)

    def test_tampered_url_and_symlink_refused(self):
        with tempfile.TemporaryDirectory() as root:
            controller = dev.Controller(root)
            state = controller.create('single')
            env = Path(state['env_file'])
            text = env.read_text()
            env.write_text(text.replace('127.0.0.1', 'prod.example'))
            with self.assertRaises(dev.OperationError):
                controller.load('single')
            env.unlink()
            elsewhere = Path(root) / 'elsewhere'
            elsewhere.write_text(text)
            env.symlink_to(elsewhere)
            with self.assertRaises(dev.OperationError):
                controller.load('single')

    def test_serena_uses_gradle_jdk_without_database_state(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.object(dev.Controller, 'java_path', return_value='/tmp/gradle-jdk/bin/java'):
                with patch.object(dev.subprocess, 'run', return_value=dev.subprocess.CompletedProcess([], 0)) as run:
                    self.assertEqual(0, dev.main(['serena'], root=Path(root)))
                    self.assertEqual('/tmp/gradle-jdk', run.call_args.kwargs['env']['JAVA_HOME'])
                    self.assertIn('start-mcp-server', run.call_args.args[0])
                    self.assertFalse((Path(root) / '.local').exists())

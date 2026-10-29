from test_dev_tasks import dev
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch


class RestoreBoundaryTest(unittest.TestCase):
    def test_missing_input_does_not_create_target_or_execute(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.object(dev.subprocess, 'run') as run:
                self.assertNotEqual(0, dev.main(['restore', '--input', str(Path(root) / 'absent')], root=Path(root)))
                run.assert_not_called()
                self.assertFalse((Path(root) / '.local').exists())

    def test_invalid_name_is_rejected_before_resource_creation(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.object(dev.subprocess, 'run') as run:
                self.assertNotEqual(0, dev.main(['up', '--name', '../../foreign'], root=Path(root)))
                run.assert_not_called()


class RestoreEnvironmentTest(unittest.TestCase):
    def test_exported_source_environment_cannot_override_new_target(self):
        for mode in ('single', 'sharding'):
            with tempfile.TemporaryDirectory() as root:
                controller = dev.Controller(root)
                source = controller.create(mode)
                source_env = controller.config(source)
                backup = Path(root) / 'backup'
                backup.write_bytes(b'fixture: subprocess boundary is mocked')
                with patch.dict(dev.os.environ, source_env):
                    with patch.object(dev.subprocess, 'run', return_value=dev.subprocess.CompletedProcess([], 0)) as run:
                        target = controller.restore(mode, backup)
                        expected = controller.config(target)
                        commands = [call for call in run.call_args_list if call.args[0][:2] == ['docker', 'compose']]
                        self.assertTrue(commands)
                        for call in commands:
                            actual = call.kwargs.get('env', {})
                            for key in ('APP_DB_PORT', 'APP_DB_PASSWORD', 'APP_DB_ADMIN_PASSWORD', 'APP_DB_MIGRATION_PASSWORD', 'APP_DB_JDBC_URL'):
                                self.assertEqual(expected[key], actual.get(key), key)
                        self.assertNotEqual(source_env['APP_DB_PASSWORD'], expected['APP_DB_PASSWORD'])
                        self.assertEqual(source_env, controller.config(source))

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

HERE = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('prepare_patch', HERE / 'prepare_patch.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PatchTest(unittest.TestCase):
    def fixture(self, root):
        for path, snippet, anchors in module.TARGETS:
            file = root / path
            file.parent.mkdir(parents=True, exist_ok=True)
            header = 'import android.os.Bundle;\n' if path.endswith('.aidl') else ''
            file.write_text(header + '// ' + ' '.join(anchors) + '\n'
                            + ('interface IWifiAwareManager' if path.endswith('.aidl') else 'class Fixture')
                            + ' {\n}\n')

    def test_patch_applies_and_rejects_partial_reapplication(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.fixture(root)
            patch = module.prepare(root)
            subprocess.run(['git', 'apply', '--check', '-'], input=patch, text=True,
                           cwd=root, check=True)
            subprocess.run(['git', 'apply', '-'], input=patch, text=True, cwd=root, check=True)
            for path, snippet, anchors in module.TARGETS:
                self.assertEqual((root / path).read_text().count(
                    (HERE / 'snippets' / snippet).read_text()), 1)
            with self.assertRaises(ValueError):
                module.prepare(root)

    def test_source_mismatch_leaves_every_source_unchanged(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.fixture(root)
            bad = root / module.TARGETS[-1][0]
            bad.write_text('class Unexpected {}\n')
            before = {p: p.read_bytes() for p in root.rglob('*') if p.is_file()}
            with self.assertRaises(ValueError):
                module.prepare(root)
            self.assertEqual(before, {p: p.read_bytes() for p in root.rglob('*') if p.is_file()})


if __name__ == '__main__':
    unittest.main()

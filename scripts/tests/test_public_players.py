import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('public_players', Path(__file__).parents[1] / 'render-public-players.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class PublicPlayersTest(unittest.TestCase):
    def test_only_public_fields_are_exported_and_owner_is_authoritative(self):
        rows = [{'name': 'Folorunsho', 'club': 'Monza', 'role': 'CENTROCAMPISTA', 'owner': 'VIKING 84',
                 'assigned': False, 'password': 'private', 'amount': 3},
                {'name': 'Libero', 'club': 'Roma', 'role': 'DIFENSORE', 'owner': None}]
        data = module.catalog(rows)
        self.assertEqual(data['rosteredCount'], 1)
        self.assertEqual(data['freeCount'], 1)
        self.assertEqual(data['players'][0]['owner'], 'VIKING 84')
        for row in data['players']:
            self.assertEqual(set(row), {'name', 'club', 'role', 'owner'})

    def test_bad_export_is_rejected_before_replacing_snapshot(self):
        row = {'name': 'Ricci', 'club': 'Milan', 'role': 'CENTROCAMPISTA', 'owner': 'Kecavoli'}
        for rows in [[], [row, row], [dict(row, role='INVALID')], [dict(row, owner=None)]]:
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                module.catalog(rows)

if __name__ == '__main__':
    unittest.main()

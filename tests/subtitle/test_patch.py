# SPDX-License-Identifier: MIT
"""Synthetic data only: no game binary or extracted cue table is a test fixture."""
import copy
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from subtitle_patch import digest, transform, write_new

class PatchTests(unittest.TestCase):
    def setUp(self):
        self.before = bytes(range(256))
        b = bytearray(self.before); b[12:16] = b'abcd'; b[200:204] = b'WXYZ'
        self.after = bytes(b)
        self.part = dict(size=256, baseline_sha256=digest(self.before),modified_sha256=digest(self.after),
            patches=[dict(offset=12,old=self.before[12:16].hex(),new=b'abcd'.hex()),
                     dict(offset=200,old=self.before[200:204].hex(),new=b'WXYZ'.hex())])

    def test_roundtrip(self):
        self.assertEqual(transform(self.before,self.part),self.after)
        self.assertEqual(transform(self.after,self.part,True),self.before)

    def test_wrong_hash(self):
        with self.assertRaises(ValueError): transform(bytes(256),self.part)

    def test_wrong_size(self):
        with self.assertRaises(ValueError): transform(self.before[:-1],self.part)

    def test_expected_bytes(self):
        self.part['patches'][0]['old']='00000000'
        with self.assertRaises(ValueError): transform(self.before,self.part)

    def test_bad_range(self):
        for offset in [-1,254,256,'12']:
            with self.subTest(offset=offset):
                p=copy.deepcopy(self.part); p['patches'][0]['offset']=offset
                with self.assertRaises(ValueError): transform(self.before,p)

    def test_different_length(self):
        self.part['patches'][0]['new']='01'
        with self.assertRaises(ValueError): transform(self.before,self.part)

    def test_overlap(self):
        self.part['patches'].append(self.part['patches'][0])
        with self.assertRaises(ValueError): transform(self.before,self.part)

    def test_output_hash(self):
        self.part['modified_sha256']='0'*64
        with self.assertRaises(ValueError): transform(self.before,self.part)

    def test_double_apply(self):
        with self.assertRaises(ValueError): transform(self.after,self.part)

    def test_files_and_overwrite_guards(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); src=root/'before'; out=root/'after'; restored=root/'restored'
            src.write_bytes(self.before)
            write_new(src,out,self.part); write_new(out,restored,self.part,True)
            self.assertEqual(src.read_bytes(),self.before)
            self.assertEqual(out.read_bytes(),self.after)
            self.assertEqual(restored.read_bytes(),self.before)
            with self.assertRaises(ValueError): write_new(src,src,self.part)
            with self.assertRaises(ValueError): write_new(src,out,self.part)
            self.assertEqual(out.read_bytes(),self.after)

if __name__=='__main__': unittest.main(verbosity=2)

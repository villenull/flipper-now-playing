import json,struct,unittest
from pathlib import Path
from reference_codec import Frame,StreamDecoder,validate_payload,ProtocolError
class ArtworkTests(unittest.TestCase):
 def vectors(self):return [bytes.fromhex(v['frame_hex']) for v in json.loads(Path(__file__).with_name('artwork_golden_vectors.json').read_text())['vectors']]
 def test_fixed_roundtrip(self):
  for b in self.vectors():self.assertEqual(Frame.decode(b).encode(),b)
 def test_semantic_rejection(self):
  p=bytearray(Frame.decode(self.vectors()[2]).payload)
  for off,val in [(8,44),(9,46),(10,2),(11,1),(17,128)]:
   bad=p.copy();bad[off]=val
   with self.assertRaises(ProtocolError):validate_payload(19,bytes(bad))
  for n in (0,11,13,281,283):
   with self.assertRaises(ProtocolError):validate_payload(19,bytes(n))
 def test_legacy_vectors_preserved(self):
  old=json.loads(Path(__file__).with_name('golden_vectors.json').read_text())['vectors']
  for v in old:self.assertEqual(Frame.decode(bytes.fromhex(v['frame_hex'])).encode().hex(),v['frame_hex'])

#!/usr/bin/env python3
"""Generate a test encrypted Aegis vault file."""

import base64
import json
import os
import uuid

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
from cryptography.hazmat.backends import default_backend

backend = default_backend()
PASSWORD = b"test"

# The plaintext vault database
db_plain = {
    "version": 3,
    "entries": [
        {
            "type": "totp",
            "uuid": str(uuid.uuid4()),
            "name": "alice@gmail.com",
            "issuer": "Google",
            "info": {
                "secret": "JBSWY3DPEHPK3PXP",
                "digits": 6,
                "algo": "SHA1",
                "period": 30,
                "counter": 0
            }
        },
        {
            "type": "totp",
            "uuid": str(uuid.uuid4()),
            "name": "dev_user",
            "issuer": "GitHub",
            "info": {
                "secret": "KRMVATZTJFZUC4TSNBHEY3LJMR",
                "digits": 6,
                "algo": "SHA1",
                "period": 30,
                "counter": 0
            }
        },
        {
            "type": "totp",
            "uuid": str(uuid.uuid4()),
            "name": "admin@company.io",
            "issuer": "AWS",
            "info": {
                "secret": "GEZDGNBVGY3TQOJQ",
                "digits": 6,
                "algo": "SHA256",
                "period": 30,
                "counter": 0
            }
        }
    ],
    "groups": []
}

db_json = json.dumps(db_plain).encode("utf-8")

# Generate master key
master_key = os.urandom(32)

# Encrypt db with master key using AES-256-GCM
db_nonce = os.urandom(12)
db_cipher = AESGCM(master_key)
db_ciphertext_with_tag = db_cipher.encrypt(db_nonce, db_json, None)
# GCM appends 16-byte tag
db_ciphertext = db_ciphertext_with_tag[:-16]
db_tag = db_ciphertext_with_tag[-16:]

# Derive key from password using scrypt
salt = os.urandom(32)
N, r, p = 32768, 8, 1
kdf = Scrypt(salt=salt, length=32, n=N, r=r, p=p, backend=backend)
derived_key = kdf.derive(PASSWORD)

# Encrypt master key with derived key
slot_nonce = os.urandom(12)
slot_cipher = AESGCM(derived_key)
encrypted_master_key_with_tag = slot_cipher.encrypt(slot_nonce, master_key, None)
encrypted_master_key = encrypted_master_key_with_tag[:-16]
slot_tag = encrypted_master_key_with_tag[-16:]

vault = {
    "version": 1,
    "header": {
        "slots": [
            {
                "type": 1,
                "uuid": str(uuid.uuid4()),
                "key": encrypted_master_key.hex(),
                "key_params": {
                    "nonce": slot_nonce.hex(),
                    "tag": slot_tag.hex()
                },
                "n": N,
                "r": r,
                "p": p,
                "salt": salt.hex()
            }
        ],
        "params": {
            "nonce": db_nonce.hex(),
            "tag": db_tag.hex()
        }
    },
    "db": base64.b64encode(db_ciphertext).decode("ascii")
}

with open("/tmp/aegis_encrypted_test.json", "w") as f:
    json.dump(vault, f, indent=2)

print("Generated /tmp/aegis_encrypted_test.json")
print(f"Password: {PASSWORD.decode()}")
print(f"Contains {len(db_plain['entries'])} entries")

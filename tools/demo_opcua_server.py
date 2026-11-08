"""
Optional OPC UA demo server for local development.

This is not part of the Spring Boot application; it gives the web console a
real OPC UA endpoint to browse, read, write and subscribe to.

    pip install asyncua cryptography
    python tools/demo_opcua_server.py

Endpoint: opc.tcp://127.0.0.1:4840
Anonymous and username (admin / admin123) are accepted.
"""
from __future__ import annotations

import asyncio
import datetime as dt
import ipaddress
import math
import os
import random
import socket
import sys
from pathlib import Path

from asyncua import Server, ua
from asyncua.crypto.permission_rules import User, UserRole
from asyncua.crypto.truststore import TrustStore
from asyncua.crypto.validator import CertificateValidator, CertificateValidatorOptions
from asyncua.server.user_managers import UserManager


ROOT = Path(__file__).resolve().parent
CERTS = ROOT / "certs"
TRUSTED = CERTS / "trusted"
DATA_PKI = (ROOT.parent / "data" / "pki").resolve()


class DemoUserManager(UserManager):
    def get_user(self, iserver, username=None, password=None, certificate=None):
        if username is None:
            return User(role=UserRole.User)
        if username == "admin" and password == "admin123":
            return User(role=UserRole.User, name="admin")
        return None


def ensure_server_certificate() -> tuple[Path, Path]:
    cert_path = CERTS / "server-cert.pem"
    key_path = CERTS / "server-key.pem"
    if cert_path.exists() and key_path.exists():
        return cert_path, key_path

    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID

    CERTS.mkdir(parents=True, exist_ok=True)
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "OPC UA Demo Server"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "OPC UA Web"),
        x509.NameAttribute(NameOID.COUNTRY_NAME, "CN"),
    ])
    san_names = [
        x509.UniformResourceIdentifier("urn:asyncua:demo:server"),
        x509.DNSName("localhost"),
        x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
    ]
    try:
        hostname = socket.gethostname()
        if hostname and hostname != "localhost":
            san_names.append(x509.DNSName(hostname))
    except Exception:
        pass
    now = dt.datetime.now(dt.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - dt.timedelta(days=1))
        .not_valid_after(now + dt.timedelta(days=3650))
        .add_extension(
            x509.SubjectAlternativeName(san_names),
            critical=False,
        )
        .add_extension(
            x509.ExtendedKeyUsage([
                ExtendedKeyUsageOID.SERVER_AUTH,
                ExtendedKeyUsageOID.CLIENT_AUTH,
            ]),
            critical=False,
        )
        .sign(key, hashes.SHA256())
    )
    cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    key_path.write_bytes(
        key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    return cert_path, key_path


def ensure_trusted_client() -> None:
    TRUSTED.mkdir(parents=True, exist_ok=True)
    candidates = [
        DATA_PKI / "client-cert.pem",
        Path(os.environ.get("CLIENT_CERT", "")),
    ]
    for candidate in candidates:
        if candidate and candidate.is_file():
            target = TRUSTED / "opcua-web-client.pem"
            target.write_bytes(candidate.read_bytes())
            return


async def main() -> None:
    cert_path, key_path = ensure_server_certificate()
    ensure_trusted_client()

    server = Server()
    await server.init()
    await server.set_application_uri("urn:asyncua:demo:server")
    server.set_endpoint("opc.tcp://0.0.0.0:4840/")
    server.set_server_name("OPC UA Web Demo Server")
    server.set_security_policy([
        ua.SecurityPolicyType.NoSecurity,
        ua.SecurityPolicyType.Basic256Sha256_Sign,
        ua.SecurityPolicyType.Basic256Sha256_SignAndEncrypt,
    ])
    await server.load_certificate(cert_path)
    await server.load_private_key(key_path)
    server.iserver.set_user_manager(DemoUserManager())

    trust_store = TrustStore(
        trust_locations=[TRUSTED],
        crl_locations=[],
    )
    validator = CertificateValidator(
        options=(
            CertificateValidatorOptions.TIME_RANGE
            | CertificateValidatorOptions.PEER_CLIENT
            | CertificateValidatorOptions.KEY_USAGE
        ),
        trust_store=trust_store,
    )
    server.set_certificate_validator(validator.validate)

    idx = await server.register_namespace("http://opcua-web/demo")
    objects = server.nodes.objects
    demo = await objects.add_object(idx, "Demo")
    line1 = await demo.add_object(idx, "Line1")

    temperature = await line1.add_variable(idx, "Temperature", 23.6, ua.VariantType.Float)
    pressure = await line1.add_variable(idx, "Pressure", 101.3, ua.VariantType.Double)
    running = await line1.add_variable(idx, "Running", True, ua.VariantType.Boolean)
    mode = await line1.add_variable(idx, "Mode", 1, ua.VariantType.Int32)
    serial = await line1.add_variable(idx, "Serial", "DEMO-2026-001", ua.VariantType.String)
    count = await line1.add_variable(idx, "Count", 100, ua.VariantType.UInt32)
    timestamp = await line1.add_variable(
        idx, "Timestamp", dt.datetime.now(dt.timezone.utc), ua.VariantType.DateTime
    )
    level_array = await line1.add_variable(
        idx,
        "LevelArray",
        ua.Variant([1.1, 2.2, 3.3], ua.VariantType.Float),
    )

    for node in (temperature, pressure, running, mode, serial, count, level_array):
        await node.set_writable()
    await serial.set_writable()

    try:
        await temperature.write_value(ua.DataValue(
            ua.Variant(23.6, ua.VariantType.Float),
            ua.StatusCode(ua.StatusCodes.Good),
            dt.datetime.now(dt.timezone.utc),
        ))
    except Exception:
        pass

    await server.start()
    print("Demo OPC UA server started")
    print("  endpoint : opc.tcp://127.0.0.1:4840")
    print("  anonymous: allowed")
    print("  username : admin / admin123")
    print("  security : None, Basic256Sha256 Sign, Basic256Sha256 SignAndEncrypt")
    print("  objects  : Objects/Demo/Line1/*")

    t = 0.0
    try:
        while True:
            t += 0.1
            await temperature.write_value(ua.Variant(25.0 + math.sin(t) * 3.5, ua.VariantType.Float))
            await pressure.write_value(ua.Variant(100.0 + random.random() * 3.0, ua.VariantType.Double))
            await count.write_value(ua.Variant(100 + int(t * 2), ua.VariantType.UInt32))
            await timestamp.write_value(ua.Variant(dt.datetime.now(dt.timezone.utc), ua.VariantType.DateTime))
            await asyncio.sleep(1.0)
    except KeyboardInterrupt:
        print("Stopping...")
    finally:
        await server.stop()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        sys.exit(0)

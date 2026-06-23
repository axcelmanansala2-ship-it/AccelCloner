package com.accel.cloner.core

import android.util.Base64
import android.util.Log
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.*
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import java.util.zip.*
import javax.security.auth.x500.X500Principal

/**
 * V1 (JAR) APK signer.
 *
 * Generates a self-signed RSA key + certificate (via BouncyCastle),
 * computes MANIFEST.MF + CERT.SF digests, then signs CERT.SF to produce
 * CERT.RSA (PKCS#7), and writes the fully signed APK.
 *
 * This is sufficient for installation on Android 8–13 when the user grants
 * "Install from unknown sources" (REQUEST_INSTALL_PACKAGES already declared).
 */
object ApkV1Signer {
    private const val TAG = "ApkV1Signer"

    @Volatile private var cachedKey: KeyPair? = null
    @Volatile private var cachedCert: X509Certificate? = null

    fun sign(unsignedApk: File, signedApk: File) {
        val (kp, cert) = keyAndCert()

        // Collect all non-META-INF entries
        data class Ent(val name: String, val data: ByteArray)
        val entries = mutableListOf<Ent>()
        ZipInputStream(BufferedInputStream(FileInputStream(unsignedApk))).use { zin ->
            var e: ZipEntry? = zin.nextEntry
            while (e != null) {
                if (!e.name.startsWith("META-INF/")) entries += Ent(e.name, zin.readBytes())
                e = zin.nextEntry
            }
        }

        val manifest = buildManifest(entries.map { it.name to it.data })
        val certSf   = buildCertSF(manifest)
        val certRsa  = buildPkcs7(certSf, kp.private, cert)

        signedApk.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(signedApk))).use { zout ->
            entries.forEach { (name, data) ->
                zout.putNextEntry(ZipEntry(name)); zout.write(data); zout.closeEntry()
            }
            zout.putNextEntry(ZipEntry("META-INF/MANIFEST.MF")); zout.write(manifest); zout.closeEntry()
            zout.putNextEntry(ZipEntry("META-INF/CERT.SF"));     zout.write(certSf);   zout.closeEntry()
            zout.putNextEntry(ZipEntry("META-INF/CERT.RSA"));    zout.write(certRsa);  zout.closeEntry()
        }
        Log.d(TAG, "Signed APK → ${signedApk.absolutePath} (${signedApk.length() / 1024} KB)")
    }

    // ── Manifest / SF builders ────────────────────────────────────────────────

    private fun buildManifest(entries: List<Pair<String, ByteArray>>): ByteArray {
        val sb = StringBuilder()
        sb.append("Manifest-Version: 1.0\r\nCreated-By: AccelCloner\r\n\r\n")
        entries.forEach { (name, data) ->
            sb.append("Name: $name\r\nSHA-256-Digest: ${sha256b64(data)}\r\n\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildCertSF(manifest: ByteArray): ByteArray {
        val sb = StringBuilder()
        sb.append("Signature-Version: 1.0\r\n")
        sb.append("Created-By: AccelCloner\r\n")
        sb.append("SHA-256-Digest-Manifest: ${sha256b64(manifest)}\r\n\r\n")

        // Digest each Name: ... section of the manifest
        val text = String(manifest, Charsets.UTF_8)
        text.split("\r\n\r\n").filter { it.startsWith("Name:") }.forEach { section ->
            val sectionBytes = (section + "\r\n\r\n").toByteArray(Charsets.UTF_8)
            val name = section.lines().first().removePrefix("Name: ").trim()
            sb.append("Name: $name\r\nSHA-256-Digest: ${sha256b64(sectionBytes)}\r\n\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildPkcs7(certSf: ByteArray, privateKey: PrivateKey, cert: X509Certificate): ByteArray {
        val gen = CMSSignedDataGenerator()
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        gen.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(
                JcaDigestCalculatorProviderBuilder().build()
            ).build(signer, cert)
        )
        gen.addCertificates(JcaCertStore(listOf(cert)))
        return gen.generate(CMSProcessableByteArray(certSf), false).encoded
    }

    // ── Key / cert ────────────────────────────────────────────────────────────

    private fun keyAndCert(): Pair<KeyPair, X509Certificate> {
        cachedKey?.let { kp -> cachedCert?.let { c -> return kp to c } }
        Log.d(TAG, "Generating RSA 2048 key pair (one-time)…")
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()

        val subject = X500Principal("CN=AccelCloner, O=AccelCloner")
        val now = Date(); val exp = Date(now.time + 30L * 365 * 24 * 60 * 60 * 1000)
        val cs = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(
            JcaX509v1CertificateBuilder(subject, BigInteger.ONE, now, exp, subject, kp.public).build(cs)
        )
        cachedKey = kp; cachedCert = cert
        return kp to cert
    }

    private fun sha256b64(data: ByteArray): String =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(data), Base64.NO_WRAP)
}

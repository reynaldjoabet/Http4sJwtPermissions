import java.net.NetworkInterface
import java.util.Base64

import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

"Bearer scope= 1 2 3 4".split("scope=")(1).split("\"")

(1 to 3).forall((1 to 3).contains)
List.empty[Int].exists((1 to 3).toList.contains)
import scala.jdk.CollectionConverters._

val interfaces =
  NetworkInterface.networkInterfaces().toList().asScala

interfaces.foreach(i => println(i.getName()))

interfaces.foreach(i => println(i.isVirtual()))

interfaces.foreach(i => println(i.isUp()))

interfaces
  .foreach(i => println(i.getDisplayName -> i.getInetAddresses().asIterator().asScala.toList))
val encoder = Base64.getEncoder()
val salt    = "saltkkkk".getBytes("UTF-8")

//A user-chosen password that can be used with password-based encryption
// The password can be viewed as some kind of raw key material, from which
//the encryption mechanism that uses it derives a cryptographic key.
val keySpec = new PBEKeySpec("hellopassword".toCharArray(), salt, 650536, 256)
//This class represents a factory for secret keys.
//Secret key factories operate only on secret (symmetric) keys
val factory   = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
val secretKey = new SecretKeySpec(factory.generateSecret(keySpec).getEncoded, "AES")
val hash      = factory.generateSecret(keySpec).getEncoded()

//the AES secret key can be derived from a given password using a password-based key derivation function like PBKDF2. We also need a salt value for turning a password into a secret key. The salt is also a random value.

def encrypt(s: String) = {
  val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
  cipher.init(Cipher.ENCRYPT_MODE, secretKey)
  val cipherText = cipher.doFinal(s.getBytes())
  encoder.encodeToString(cipherText)
}

encrypt("Hello")

encrypt("Hello")

encrypt("Hello")

//Bits of entropy measure how difficult a password is to crack
// A measure of strength
//E=Lxlog2(R)
// E-> password entropy
//log2 is a mathematical formula that converts the total number of possible character combinations to bits
//R-> range of characters
//L ->number of characters in password

// A password entropy of 64 bits approximates to 2^64( from log... log5=x implies 10^x=5)

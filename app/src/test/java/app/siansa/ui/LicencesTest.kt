
    /** #584: the repository is public, so the GPL text is in and the signing key isn't. */
    @Test fun theLicenceIsInAndTheKeyIsOut() {
        val root = listOf(File(".."), File(".")).first { File(it, "LICENSE").isFile }
        val licence = File(root, "LICENSE").readText()
        assertTrue("LICENSE isn't the GPL 3", licence.contains("GNU GENERAL PUBLIC LICENSE") && licence.contains("Version 3, 29 June 2007"))
        assertTrue("The signing key is in the tree", !File(root, "app/siansa.keystore").exists())
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue("A password is in build.gradle.kts", !gradle.contains("REDACTED"))
        assertTrue("The key isn't read from SIANSA_KEYSTORE", gradle.contains("SIANSA_KEYSTORE"))
    }
}

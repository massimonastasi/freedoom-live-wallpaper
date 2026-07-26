// Imported explicitly: inside the Gradle Kotlin DSL, `java` resolves to the plugin
// accessor rather than the package, so a fully qualified java.io.* reference fails.
import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.massimonastasi.freedoomlw"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.massimonastasi.freedoomlw"
        // minSdk 31: Material You (onComputeColors -> system theme) arrived with Android 12.
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // ponytail: no compression on .wad files - they are read with random access from assets.
    androidResources {
        noCompress += "wad"
    }

    // Enabled for BuildConfig.DEBUG alone, which gates the on-screen debug overlay. Tying it
    // to the build type rather than to a constant is what stops the overlay ever shipping.
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// ponytail: no runtime dependencies. WallpaperService and Canvas are part of the framework.
// Only the tests have dependencies, and those never reach the APK.
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

/**
 * Writes the bundled asset WAD, keeping only the lumps this wallpaper reads.
 *
 * The full Freedoom IWAD is 27.5 MB and we use 3.2% of it: the rest is maps, wall textures,
 * menu graphics, music and sound effects. The output stays a WAD rather than a folder of
 * PNGs so the app keeps a single loader: the patch format carries the anchor offsets that
 * stop sprites jittering, and the rotation and mirroring convention lives in the lump names.
 * Flattening to images would need a side table for both and a second load path.
 *
 * Run with: gradlew reduceWad
 * Input:  app/wad/freedoom-full.wad          (downloaded, not in the repo)
 * Output: app/src/main/assets/freedoom1.wad  (what ships)
 *
 * The input deliberately sits outside assets. Left in there it would be packaged as well,
 * and the APK would carry both copies.
 *
 * WadFileTest asserts the shipped WAD still satisfies everything the code asks for, so a
 * drift between this list and GameData fails the build rather than the wallpaper.
 */
tasks.register("reduceWad") {
    val source = layout.projectDirectory.file("wad/freedoom-full.wad").asFile
    val target = layout.projectDirectory.file("src/main/assets/freedoom1.wad").asFile

    doLast {
        require(source.exists()) { "missing ${source.name}: download Freedoom and rename the IWAD to it" }

        // Sprite prefixes, mirroring GameData: creatures, the marine, projectiles, pickups
        // and effects. Diagonal views are dropped because nothing moves diagonally.
        val spritePrefixes = listOf(
            "POSS", "SPOS", "TROO", "SARG", "HEAD", "BOSS", "PLAY",
            "BAL1", "BAL7", "BLUD", "TFOG",
            "STIM", "MEDI", "ARM1", "ARM2", "SHOT", "MGUN",
        )
        val exactNames = buildSet {
            // Freedoom's own identifying lump. Seven bytes, and without it the reduced
            // asset no longer says what it is derived from.
            add("FREEDOOM")
            add("PLAYPAL")                    // palette, and the damage flash ramp
            // One floor flat per skill level, plus the fallbacks the loader walks when a
            // user-supplied WAD is missing one. Restated here because GameData is app code
            // and not on the build script's classpath; WadFileTest fails if the two drift.
            addAll(listOf("FLAT4", "RROCK13", "GRNROCK", "BLOOD1", "RROCK01"))
            addAll(listOf("RROCK03", "FLOOR1_6", "FLAT14", "FLOOR0_1"))
            add("F_START"); add("F_END")      // flat markers: flatIndex searches between them
            for (d in 0..9) add("STTNUM$d")   // readout numerals
        }
        val keptRotations = charArrayOf('0', '1', '3', '5', '7')

        fun needed(name: String): Boolean {
            if (name in exactNames) return true
            val prefix = spritePrefixes.firstOrNull { name.startsWith(it) } ?: return false
            if (name.length != prefix.length + 2 && name.length != prefix.length + 4) return false
            // Keep the lump if it covers any rotation still in use; a mirrored pair carries
            // two, and rotation 0 means it serves every angle.
            return name.drop(prefix.length).filterIndexed { i, _ -> i % 2 == 1 }.any { it in keptRotations }
        }

        val bytes = source.readBytes()
        fun int(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 3].toInt() and 0xFF) shl 24)

        val count = int(4)
        val dir = int(8)
        val kept = mutableListOf<Triple<String, Int, Int>>()      // name, offset, size
        for (i in 0 until count) {
            val e = dir + i * 16
            val name = String(bytes, e + 8, 8, Charsets.US_ASCII).trimEnd('\u0000')
            if (needed(name)) kept += Triple(name, int(e), int(e + 4))
        }

        val out = ByteArrayOutputStream()
        fun writeInt(v: Int) { out.write(v); out.write(v shr 8); out.write(v shr 16); out.write(v shr 24) }

        out.write("IWAD".toByteArray(Charsets.US_ASCII))
        writeInt(kept.size)
        writeInt(0)                                              // directory offset, patched below
        val offsets = kept.map { (_, off, size) ->
            val here = out.size()
            out.write(bytes, off, size)
            here
        }
        val dirOffset = out.size()
        kept.forEachIndexed { i, (name, _, size) ->
            writeInt(offsets[i])
            writeInt(size)
            out.write(name.padEnd(8, '\u0000').toByteArray(Charsets.US_ASCII), 0, 8)
        }
        val result = out.toByteArray()
        result[8] = dirOffset.toByte()
        result[9] = (dirOffset shr 8).toByte()
        result[10] = (dirOffset shr 16).toByte()
        result[11] = (dirOffset shr 24).toByte()
        target.writeBytes(result)

        val before = source.length() / 1024
        val after = target.length() / 1024
        logger.lifecycle("reduceWad: ${kept.size} of $count lumps, $before KB -> $after KB")
    }
}

package com.iven.musicplayergo.preferences

import com.iven.musicplayergo.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Exercise the shipped default resource templates without an Android runtime. */
private val defaultStrings: Map<Int, String> by lazy {
    val names = R.string::class.java.fields.associate { it.name to it.getInt(null) }
    val directory = listOf(File("src/main/res/values"), File("app/src/main/res/values"))
        .first { it.isDirectory }
    directory.listFiles()!!.filter { it.extension == "xml" }.flatMap { file ->
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        (0 until strings.length).mapNotNull { i ->
            val node = strings.item(i)
            names[node.attributes.getNamedItem("name").nodeValue]?.let { id ->
                id to node.textContent.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"")
            }
        }
    }.toMap()
}

fun UiText.english(): String = render { id, args -> String.format(Locale.ENGLISH, defaultStrings.getValue(id), *args.toTypedArray()) }
fun UiText.contains(value: String): Boolean = english().contains(value)

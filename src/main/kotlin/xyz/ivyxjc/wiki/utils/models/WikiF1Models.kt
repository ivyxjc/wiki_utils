package xyz.ivyxjc.wiki.utils.models

import org.apache.commons.lang3.builder.ToStringBuilder


class F1Driver(val number: Int) {
    lateinit var name: String
    var rank: Int = Int.MIN_VALUE
    var pole = false
    var fastestLap = false
    var dagger = false

    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this)
    }
}

class F1Team(val name: String) {
    lateinit var driver1: F1Driver
    lateinit var driver2: F1Driver
    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this)
    }
}

class F1Match(val name: String) {
    var f1Teams = mutableMapOf<String, F1Team>()
    var f1Drivers = mutableMapOf<Int, F1Driver>()
    var pole = F1Driver(-1)
    var fastedLap = F1Driver(-1)

    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this)
    }
}

class F1Season(val year: String) {
    lateinit var matches: MutableMap<String, F1Match>
}

class WikiF1Team(val name: String) {
    val driver1Builder = DriverBuilder()
    val driver2Builder = DriverBuilder()
}


class DriverBuilder {
    var driverNum: Int = -1
    val builder = StringBuilder()

    fun append(str: String) {
        builder.append(str)
    }

    override fun toString(): String {
        return builder.toString()
    }
}
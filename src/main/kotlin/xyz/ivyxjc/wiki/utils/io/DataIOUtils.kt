package xyz.ivyxjc.wiki.utils.io

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.lang3.StringUtils
import xyz.ivyxjc.wiki.utils.models.F1Driver
import xyz.ivyxjc.wiki.utils.models.F1Match
import xyz.ivyxjc.wiki.utils.models.F1Team
import java.io.InputStreamReader


private val topLevelClass = object : Any() {}.javaClass.enclosingClass

val playerNumbers = arrayOf(44, 77, 5, 16, 33, 10, 55, 4, 26, 23, 3, 27, 7, 99, 18, 11, 20, 8, 88, 63)

val points = mutableMapOf<Int, Int>()

val matches = mutableListOf<String>()

fun calPoint(driver: F1Driver): Int {
    if (points.isEmpty()) {
        val gson = Gson()
        val type = object : TypeToken<Map<String, String>>() {}.type
        val map = gson.fromJson(
            InputStreamReader(topLevelClass.classLoader.getResourceAsStream("2019/rankPoint.json")!!),
            type
        ) as Map<Any, Any>
        points.clear()
        map.forEach { (key, value) ->
            points[key.toString().toInt()] = value.toString().toInt()
        }
    }
    var point = if (points[driver.rank] != null) {
        points[driver.rank]!!
    } else {
        0
    }
    if (driver.fastestLap) {
        point++
    }
    return point
}

const val YEAR = 2019

fun driverCsv(path: String = "$YEAR/driver_status.csv"): Map<Int, F1Match> {
    val input = InputStreamReader(topLevelClass.classLoader.getResourceAsStream(path)!!)
    val csvParser = CSVParser(input, CSVFormat.DEFAULT)
    val matchMap = mutableMapOf<Int, F1Match>()
    csvParser.forEachIndexed loop1@{ i, csvRecord ->
        if (i == 0) {
            csvRecord.forEachIndexed { j, s ->
                if (j != 0) {
                    matchMap[j] = F1Match(s.trim())
                }
            }
            return@loop1
        }
        var driverNum: Int = -1
        csvRecord.forEachIndexed { j, s ->
            if (j == 0) {
                driverNum = s.trim().toInt()
            } else {
                val f1Driver = F1Driver(driverNum)
                if (s.contains("p")) {
                    f1Driver.rank = s.split("-")[0].toInt()
                    matchMap[j]!!.pole = f1Driver
                    f1Driver.pole = true
                }
                if (s.contains("f")) {
                    f1Driver.rank = s.split("-")[0].toInt()
                    matchMap[j]!!.fastedLap = f1Driver
                    f1Driver.fastestLap = true
                }
                if (!s.contains("p") && !s.contains("f")) {
                    f1Driver.rank = s.trim().toInt()
                }
                matchMap[j]!!.f1Drivers[driverNum] = f1Driver
            }
        }
    }
    return matchMap
}


fun teamCsv(
    path: String = "$YEAR/team_status.csv"
): Map<Int, F1Match> {
    matches.clear()
    val input = InputStreamReader(topLevelClass.classLoader.getResourceAsStream(path)!!)
    val csvParser = CSVParser(input, CSVFormat.DEFAULT)
    val matchMap = mutableMapOf<Int, F1Match>()
    csvParser.forEachIndexed loop1@{ index, csvRecord ->
        if (index == 0) {
            csvRecord.forEachIndexed { j, s ->
                if (j != 0) {
                    matchMap[j] = F1Match(s)
                    matches.add(s!!)
                }
            }
            return@loop1
        }
        var teamName: String = ""
        csvRecord.forEachIndexed { j, s ->
            if (j == 0) {
                teamName = s.trim()
            } else {
                if (matchMap[j]!!.f1Teams.containsKey(teamName)) {
                    matchMap[j]!!.f1Teams[teamName]!!.driver2 = F1Driver(s.trim().toInt())
                } else {
                    val f1Team = F1Team(teamName)
                    matchMap[j]!!.f1Teams[teamName] = f1Team
                    f1Team.driver1 = F1Driver(s.trim().toInt())
                }
            }
        }

    }
    return matchMap
}

fun convert(map: Map<Int, F1Match>): Map<String, F1Match> {
    val res = mutableMapOf<String, F1Match>()
    map.entries.forEach {
        res[it.value.name] = it.value
    }
    return res
}

fun combine(driverMap: Map<Int, F1Match>, teamMap: Map<Int, F1Match>): Map<String, F1Match> {
    val driverMapTmp = convert(driverMap)
    val finalMap = convert(teamMap)
    finalMap.forEach { matchName, match ->
        val driverMatch = driverMapTmp[matchName] ?: error("driver map does not contain match $matchName")
        match.f1Teams.forEach {
            it.value.driver1 = driverMatch.f1Drivers[it.value.driver1.number]!!
            it.value.driver2 = driverMatch.f1Drivers[it.value.driver2.number]!!
            match.f1Drivers[it.value.driver1.number] = it.value.driver1
            match.f1Drivers[it.value.driver2.number] = it.value.driver2
        }
    }
    return finalMap
}

fun calPoint(f1Team: F1Team): Int {
    return calPoint(f1Team.driver1) + calPoint(f1Team.driver2)
}

fun calTeamPoints(endMatchName: String, map: Map<String, F1Match>): Map<String, Int> {
    val res = mutableMapOf<String, Int>()
    matches.forEachIndexed { index, matchName ->
        val match = map[matchName]
        if (index == 0) {
            match!!.f1Teams.forEach {
                res[it.key] = 0
            }
        }

        match!!.f1Teams.forEach {
            res[it.key] = res[it.key]!! + calPoint(it.value)
        }
        if (StringUtils.equals(matchName, endMatchName)) {
            return res
        }
    }
    return res
}

fun main() {
    println("++++++++++++++++++++")
    val finalMap = combine(driverCsv(), teamCsv())
//    println(finalMap["Australia"]!!.f1Drivers)
    val res = calTeamPoints("Hungary", finalMap)
    println(res)
}


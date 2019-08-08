package xyz.ivyxjc.wiki.utils.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.lang3.StringUtils
import xyz.ivyxjc.wiki.utils.models.*
import xyz.ivyxjc.wiki.utils.utils.Constants
import java.io.FileWriter
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.*
import kotlin.Comparator

class WikiHandler {
    private val classLoader = object : Any() {}.javaClass.enclosingClass.classLoader
    private val teams: List<String>
    private val drivers: MutableList<Int>
    private val teamsCountry: MutableMap<String, String>
    private val driversCountry: MutableMap<Int, String>
    private val teamsDesc: MutableMap<String, String>
    private val driversDesc: MutableMap<Int, String>
    private val playerIDs: List<Int>
    private val matchesInOrder: List<String>
    private val rankPoints = mutableMapOf<Int, Int>()

    private val driverStatusPath = "${Constants.YEAR}/driver_status.csv"
    private val teamStatusPath = "${Constants.YEAR}/team_status.csv"


    companion object {
        private val seasonTeamStr = """
        |-
        !rowspan="2"|%{team_rank}
        |rowspan="2" align="left"|{{flagicon|%{team_country}}} [[%{team_name}]]
        
    """.trimIndent()
        private val seasonDriverStr = """
            |-
            !%{driver_rank}
            |style="text-align:left"|{{flagicon|%{driver_country}}} [[%{driver_name}]]
        
    """.trimIndent()

        private fun convertRankToDesc(driver: F1Driver): String {
            val rank = driver.rank
            if (driver.rank > 0) {
                if (driver.pole && driver.fastestLap) {
                    return checkDagger("'''''${driver.rank}'''''", driver)
                }
                if (driver.pole) {
                    return checkDagger("'''${driver.rank}'''", driver)
                }
                if (driver.fastestLap) {
                    return checkDagger("''${driver.rank}''", driver)
                }

                return checkDagger("${driver.rank}", driver)
            }

            return when (driver.rank) {
                -1 -> checkDagger("Ret", driver)
                else -> checkDagger("Ret", driver)
            }
        }

        private fun checkDagger(str: String, driver: F1Driver): String {
            if (driver.dagger) {
                return str + "{{dagger}}"
            } else {
                return str
            }
        }
    }

    init {
        val properties = Properties()
        properties.load(
            InputStreamReader(
                classLoader.getResourceAsStream("${Constants.YEAR}/config.properties"),
                Charset.defaultCharset()
            )
        )
        val teamsStr = properties["teams"]!! as String
        val driverStr = properties["driverIds"]!! as String
        val teamsCountryStr = properties["teamsCountry"]!! as String
        val teamsDescStr = properties["teamsDesc"]!! as String

        val matchesInOrderStr = properties["matcesInOrder"]!! as String

        val playerIdsStr = properties["driverIds"]!! as String
        val playersCountryStr = properties["driversCountry"]!! as String
        val playersDescStr = properties["driversDesc"]!! as String

        val teamsCountryStrArr = teamsCountryStr.split(",")
        val teamsDescStrArr = teamsDescStr.split(",")

        val driversCountryStrArr = playersCountryStr.split(",")
        val driversDescStrArr = playersDescStr.split(",")

        teams = teamsStr.split(",")
        matchesInOrder = matchesInOrderStr.split(",")

        val driversStrArr = driverStr.split(",")

        if (teams.size != teamsCountryStrArr.size || teams.size != teamsDescStrArr.size) {
            throw RuntimeException("team's size should be equal to team country's size")
        }

        if (driversStrArr.size != driversCountryStrArr.size || driversStrArr.size != driversDescStrArr.size) {
            throw RuntimeException("team's size should be equal to team country's size")
        }

        teamsCountry = mutableMapOf()
        teamsDesc = mutableMapOf()
        for (i in 0 until teams.size) {
            teamsCountry[teams[i]] = teamsCountryStrArr[i]
            teamsDesc[teams[i]] = teamsDescStrArr[i]
        }

        drivers = mutableListOf()
        driversCountry = mutableMapOf()
        driversDesc = mutableMapOf()
        for (i in 0 until driversStrArr.size) {
            drivers.add(driversStrArr[i].trim().toInt())
            driversCountry[drivers[i]] = driversCountryStrArr[i].trim()
            driversDesc[drivers[i]] = driversDescStrArr[i].trim()
        }

        val playerIdsStrArr = playerIdsStr.split(",")
        playerIDs = List(playerIdsStrArr.size) {
            playerIdsStrArr[it].trim().toInt()
        }
        val gson = Gson()
        val type = object : TypeToken<Map<String, String>>() {}.type
        val tmpMap = gson.fromJson(
            InputStreamReader(classLoader.getResourceAsStream("2019/rankPoint.json")!!),
            type
        ) as Map<Any, Any>
        tmpMap.forEach { (key, value) ->
            rankPoints[key.toString().toInt()] = value.toString().trim().toInt()
        }


    }

    fun seasonDriverStatus(endMatchName: String): String {
        val map = combine(parseDriverCsv(driverStatusPath), parseTeamCsv(teamStatusPath))
        val driverPoints = mutableMapOf<Int, Int>()
        val driverRank = mutableSetOf<Int>()
        run {
            matchesInOrder.forEachIndexed loop@{ index, matchName ->
                val match = map[matchName] ?: throw RuntimeException("$matchName is not finish")
                if (index == 0) {
                    match.f1Drivers.forEach {
                        driverPoints[it.key] = 0
                    }
                }
                match.f1Drivers.forEach {
                    driverPoints[it.key] = driverPoints[it.key]!! + calPoint(it.value)
                }
                if (StringUtils.equals(matchName, endMatchName)) {
                    return@run
                }
            }
        }
        val sortedDriverPoints =
            driverPoints.entries.sortedWith(Comparator { o1: Map.Entry<Int, Int>, o2: Map.Entry<Int, Int> ->
                return@Comparator o2.value.compareTo(o1.value)
            })
        sortedDriverPoints.forEach {
            driverRank.add(it.key)
        }

        val wikiDriverSeason = mutableMapOf<Int, DriverBuilder>()
        /*
        key: teamName, value: WikiF1Team
         */
        matchesInOrder.forEachIndexed { index, matchName ->
            val driver = map[matchName]
            if (index == 0) {
                driver!!.f1Drivers.forEach {
                    wikiDriverSeason[it.key] = DriverBuilder()
                    wikiDriverSeason[it.key]!!.driverNum = it.value.number
                }
            }
            if (driver == null) {
                drivers.forEach {
                    wikiDriverSeason[it]!!.append("|\n")
                }
            } else {
                driver.f1Drivers.forEach {
                    wikiDriverSeason[it.key]!!.append(buildWikiStr(it.value))
                }
            }

        }
        driverRank.forEachIndexed { index, s ->
            val rank = index + 1
            val teamCountry = getDriverCountry(s)
            val teamName = getDriverDesc(s)
            var seasonDriverRes = seasonDriverStr
            seasonDriverRes = seasonDriverRes.replace("%{driver_rank}", rank.toString())
            seasonDriverRes = seasonDriverRes.replace("%{driver_country}", teamCountry)
            seasonDriverRes = seasonDriverRes.replace("%{driver_name}", teamName)
            seasonDriverRes += wikiDriverSeason[s].toString() + "!rowspan=\"2\" |${driverPoints[s]}\n|-"

            println(seasonDriverRes)
            write(seasonDriverRes)
        }

        return ""
    }

    fun write(data: String) {
        val fw = FileWriter("C:\\IVY\\temp\\abc.txt")
        fw.write(data)
        fw.flush()
    }

    fun seasonTeamStatus(endMatchName: String): String {
        val map = combine(parseDriverCsv(driverStatusPath), parseTeamCsv(teamStatusPath))
        val driverPoints = mutableMapOf<Int, Int>()
        val driverRank = mutableSetOf<Int>()
        val teamPoints = mutableMapOf<String, Int>()
        val teamRank = mutableSetOf<String>()
        run {
            matchesInOrder.forEachIndexed loop@{ index, matchName ->
                val match = map[matchName] ?: throw RuntimeException("$matchName is not finish")
                if (index == 0) {
                    match.f1Teams.forEach {
                        teamPoints[it.key] = 0
                    }
                    match.f1Drivers.forEach {
                        driverPoints[it.key] = 0
                    }
                }
                match.f1Teams.forEach {
                    teamPoints[it.key] = teamPoints[it.key]!! + calPoint(it.value)
                }
                match.f1Drivers.forEach {
                    driverPoints[it.key] = driverPoints[it.key]!! + calPoint(it.value)
                }
                if (StringUtils.equals(matchName, endMatchName)) {
                    return@run
                }
            }
        }
        val sortedDriverPoints =
            driverPoints.entries.sortedWith(Comparator { o1: Map.Entry<Int, Int>, o2: Map.Entry<Int, Int> ->
                return@Comparator o2.value.compareTo(o1.value)
            })
        sortedDriverPoints.forEach {
            driverRank.add(it.key)
        }

        val sortedTeamPoints =
            teamPoints.entries.sortedWith(Comparator { o1: Map.Entry<String, Int>, o2: Map.Entry<String, Int> ->
                return@Comparator o2.value.compareTo(o1.value)
            })
        sortedTeamPoints.forEach {
            teamRank.add(it.key)
        }


        val wikiSeason = mutableMapOf<String, WikiF1Team>()
        /*
        key: teamName, value: WikiF1Team
         */
        matchesInOrder.forEachIndexed { index, matchName ->
            val match = map[matchName]
            if (index == 0) {
                match!!.f1Teams.forEach {
                    wikiSeason[it.key] = WikiF1Team(it.key)
                    wikiSeason[it.key]!!.driver1Builder.driverNum = it.value.driver1.number
                    wikiSeason[it.key]!!.driver2Builder.driverNum = it.value.driver2.number
                    wikiSeason[it.key]!!.driver1Builder.append("| ${it.value.driver1.number}\n")
                    wikiSeason[it.key]!!.driver2Builder.append("| ${it.value.driver2.number}\n")
                }
            }
            if (match == null) {
                teams.forEach {
                    wikiSeason[it]!!.driver1Builder.append("|\n")
                    wikiSeason[it]!!.driver2Builder.append("|\n")
                }
            } else {
                match.f1Teams.forEach {
                    wikiSeason[it.key]!!.driver1Builder.append(buildWikiStr(it.value.driver1))
                    wikiSeason[it.key]!!.driver2Builder.append(buildWikiStr(it.value.driver2))
                }
            }

        }
        teamRank.forEachIndexed { index, s ->
            val rank = index + 1
            val teamCountry = getTeamCountry(s)
            val teamName = getTeamDesc(s)
            var seasonTeamRes = seasonTeamStr
            seasonTeamRes = seasonTeamRes.replace("%{team_rank}", rank.toString())
            seasonTeamRes = seasonTeamRes.replace("%{team_country}", teamCountry)
            seasonTeamRes = seasonTeamRes.replace("%{team_name}", teamName)
            val d1Rank = driverRank.find { it == wikiSeason[s]!!.driver1Builder.driverNum }
            val d2Rank = driverRank.find { it == wikiSeason[s]!!.driver2Builder.driverNum }
            val d1Builder: DriverBuilder
            val d2Builder: DriverBuilder
            if (d1Rank!! < d2Rank!!) {
                d1Builder = wikiSeason[s]!!.driver1Builder
                d2Builder = wikiSeason[s]!!.driver2Builder
            } else {
                d1Builder = wikiSeason[s]!!.driver2Builder
                d2Builder = wikiSeason[s]!!.driver1Builder
            }
            seasonTeamRes += d1Builder.toString() + "!rowspan=\"2\" |${teamPoints[s]}\n|-\n" + d2Builder.toString()

            println(seasonTeamRes)
        }

        return ""
    }


    private fun getTeamCountry(country: String): String {
        return teamsCountry[country]!!
    }

    private fun getTeamDesc(name: String): String {
        return teamsDesc[name]!!
    }

    private fun getDriverCountry(num: Int): String {
        return driversCountry[num]!!
    }

    private fun getDriverDesc(number: Int): String {
        return driversDesc[number]!!
    }


    private fun buildWikiStr(driver: F1Driver): String {
        var color: String = ""
        if (driver.rank == 1) {
            color = "#ffffbf"
        } else if (driver.rank == 2) {
            color = "#dfdfdf"
        } else if (driver.rank == 3) {
            color = "#ffdf9f"
        } else if (driver.rank <= 10 && driver.rank > 3) {
            color = "#dfffdf"
        } else if (driver.rank > 10) {
            color = "#cfcfff"
        } else if (driver.rank == -1) {
            color = "#efcfff"
        }
        return "|style=\"background-color:$color\"|${convertRankToDesc(driver)}\n"
    }

    private fun calPoint(driver: F1Driver): Int {
        var point = if (rankPoints[driver.rank] != null) {
            rankPoints[driver.rank]!!
        } else {
            0
        }
        if (driver.fastestLap) {
            point++
        }
        return point
    }

    private fun parseDriverCsv(path: String): Map<Int, F1Match> {
        val input = InputStreamReader(classLoader.getResourceAsStream(path)!!)
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
                    if (s.contains("d")) {
                        f1Driver.rank = s.split("-")[0].toInt()
                        f1Driver.dagger = true
                    }
                    if (!s.contains("p") && !s.contains("f") && !s.contains("d")) {
                        f1Driver.rank = s.trim().toInt()
                    }
                    matchMap[j]!!.f1Drivers[driverNum] = f1Driver
                }
            }
        }
        return matchMap
    }

    private fun parseTeamCsv(path: String): Map<Int, F1Match> {
        val input = InputStreamReader(classLoader.getResourceAsStream(path)!!)
        val csvParser = CSVParser(input, CSVFormat.DEFAULT)
        val matchMap = mutableMapOf<Int, F1Match>()
        csvParser.forEachIndexed loop1@{ index, csvRecord ->
            if (index == 0) {
                csvRecord.forEachIndexed { j, s ->
                    if (j != 0) {
                        matchMap[j] = F1Match(s)

                    }
                }
                return@loop1
            }
            var teamName = ""
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

    private fun convert(map: Map<Int, F1Match>): Map<String, F1Match> {
        val res = mutableMapOf<String, F1Match>()
        map.entries.forEach {
            res[it.value.name] = it.value
        }
        return res
    }

    /**
     * key is matchName
     * value is F1Match
     */
    private fun combine(driverMap: Map<Int, F1Match>, teamMap: Map<Int, F1Match>): Map<String, F1Match> {
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

    private fun calPoint(f1Team: F1Team): Int {
        return calPoint(f1Team.driver1) + calPoint(f1Team.driver2)
    }

    private fun calTeamPoints(endMatchName: String, map: Map<String, F1Match>): Map<String, Int> {
        val res = mutableMapOf<String, Int>()
        matchesInOrder.forEachIndexed { index, matchName ->
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


}

fun main() {
    val handler = WikiHandler()
//    handler.seasonTeamStatus("HUN")
    handler.seasonDriverStatus("HUN")
}
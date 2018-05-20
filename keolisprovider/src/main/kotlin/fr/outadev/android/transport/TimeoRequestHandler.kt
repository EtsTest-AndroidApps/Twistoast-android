/*
 * Twistoast - TimeoRequestHandler.kt
 * Copyright (C) 2013-2018 Baptiste Candellier
 *
 * Twistoast is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Twistoast is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.outadev.android.transport

import android.util.Log
import fr.outadev.android.transport.dto.ErreurDTO
import fr.outadev.android.transport.dto.ListeHorairesDTO
import fr.outadev.android.transport.dto.ListeLignesDTO
import fr.outadev.android.transport.dto.MessageDTO
import fr.outadev.twistoast.model.*
import org.apache.commons.lang3.StringUtils.leftPad
import org.json.JSONObject
import org.json.JSONTokener
import org.simpleframework.xml.core.Persister
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.net.URLEncoder
import java.util.*

/**
 * Handles all connections to the Twisto Realtime API.
 *
 * @author outadoc
 */
class TimeoRequestHandler (val http: IHttpRequester = HttpRequester()) : ITimeoRequestHandler {

    private val serializer: Persister = Persister()

    /**
     * Fetches the bus stops for the specified line.
     */
    @Throws(IOException::class, DataProviderException::class)
    override fun getStops(line: Line): List<Stop> {
        return getStops(line.networkCode, line)
    }

    /**
     * Fetches the next bus schedules for the specified bus stop.
     */
    @Throws(DataProviderException::class, IOException::class, XmlPullParserException::class)
    override fun getSingleSchedule(stop: Stop): StopSchedule {
        return getSingleSchedule(stop.line.networkCode, stop)
    }

    /**
     * Fetches the next bus schedules for the specified list of bus stops.
     */
    @Throws(DataProviderException::class, IOException::class)
    override fun getMultipleSchedules(stops: List<Stop>): List<StopSchedule> {
        //if we don't specify any network code when calling getMultipleSchedules, we'll have to figure them out ourselves.
        //we can only fetch a list of schedules that are all part of the same network.
        //therefore, we'll have to separate them in different lists and request them individually.

        //the final list that will contain all of our schedules
        val finalScheduleList = ArrayList<StopSchedule>()
        val stopsByNetwork = stops.groupBy { it.line.networkCode }

        Log.i(TAG, "${stopsByNetwork.count()} different bus networks to refresh")

        stopsByNetwork.forEach {
            //request the schedules and add them to the final list
            finalScheduleList.addAll(getMultipleSchedules(it.key, it.value))
        }

        return finalScheduleList
    }


    @Throws(IOException::class, DataProviderException::class)
    override fun getLines(networkCode: Int): List<Line> {
        val params = "xml=1"

        val result = http.requestWebPage(getEndpointUrl(networkCode), params, true)
        val res: ListeLignesDTO = serializer.read(ListeLignesDTO::class.java, result) ?: throw DataProviderException("Service returned invalid data")

        checkForErrors(res.erreur)

        return res.alss.map {
                    als ->
            Line(
                    id = als.ligne.code,
                    name = als.ligne.nom.smartCapitalize(),
                    direction = Direction(als.ligne.sens, als.ligne.vers?.smartCapitalize()),
                    color = "#" + leftPad(Integer.toHexString(Integer.valueOf(als.ligne.couleur)), 6, '0'),
                    networkCode = networkCode)
                }
    }

    @Throws(IOException::class, DataProviderException::class)
    override fun getStops(networkCode: Int, line: Line): List<Stop> {
        val params = "xml=1&ligne=${line.id}&sens=${line.direction.id}"

        val result = http.requestWebPage(getEndpointUrl(networkCode), params, true)
        val res: ListeLignesDTO = serializer.read(ListeLignesDTO::class.java, result) ?: throw DataProviderException("Service returned invalid data")

        checkForErrors(res.erreur)

        return res.alss.filter { it.arret.code != null && it.arret.nom != null }.map {
            als ->
            Stop(
                    id = als.arret.code!!.toInt(),
                    name = als.arret.nom!!.smartCapitalize(),
                    reference = als.refs,
                    line = Line(
                            id = als.ligne.code,
                            name = als.ligne.nom.smartCapitalize(),
                            direction = Direction(als.ligne.sens, als.ligne.vers?.smartCapitalize()),
                            color = "#" + leftPad(Integer.toHexString(Integer.valueOf(als.ligne.couleur)), 6, '0'),
                            networkCode = networkCode))
        }
    }

    /**
     * Retrieve a list of stops by their code.
     * Useful to get a stop's info when they're only known by their code.
     */
    @Throws(IOException::class, DataProviderException::class)
    override fun getStopsByCode(networkCode: Int, codes: List<Int>): List<Stop> {
        val codesCat = codes
                .filterNot { code -> code == 0 }
                .joinToString(",")

        val params = "xml=1&code=$codesCat"

        val result = http.requestWebPage(getEndpointUrl(networkCode), params, true)
        val res: ListeLignesDTO = serializer.read(ListeLignesDTO::class.java, result) ?: throw DataProviderException("Service returned invalid data")

        checkForErrors(res.erreur)

        return res.alss
                .filter { it.arret.code != null }
                .filter { it.arret.nom != null }
                .map { als ->
                    Stop(
                            id = als.arret.code!!.toInt(),
                            name = als.arret.nom!!.smartCapitalize(),
                            reference = als.refs,
                            line = Line(
                                    id = als.ligne.code,
                                    name = als.ligne.nom.smartCapitalize(),
                                    direction = Direction(als.ligne.sens, als.ligne.vers?.smartCapitalize()),
                                    color = "#" + leftPad(Integer.toHexString(Integer.valueOf(als.ligne.couleur)), 6, '0'),
                                    networkCode = networkCode))
                }
    }

    /**
     * Fetches the next bus schedules for the specified bus stop.
     */
    @Throws(DataProviderException::class, IOException::class, XmlPullParserException::class)
    override fun getSingleSchedule(networkCode: Int, stop: Stop): StopSchedule {
        val schedules = getMultipleSchedules(networkCode, listOf(stop))

        if (schedules.isNotEmpty()) {
            return schedules[0]
        } else {
            throw DataProviderException("No schedules were returned.")
        }
    }

    /**
     * Fetches the next bus schedules for the specified list of bus stops.
     */
    @Throws(DataProviderException::class, IOException::class)
    override fun getMultipleSchedules(networkCode: Int, stops: List<Stop>): List<StopSchedule> {
        // If no stops are in the list or all refs are null
        if (stops.all { stop -> stop.reference == null }) {
            return listOf()
        }

        // Append the stop references to the refs to send to the API
        val refs = stops.filter { stop -> stop.reference != null }
                        .map { stop -> stop.reference }
                        .joinToString(";")

        val params = "xml=3&refs=${URLEncoder.encode(refs, "UTF-8")}&ran=1"

        val result = http.requestWebPage(getEndpointUrl(networkCode), params, false)
        val cleanResult = result.replace(" {6}<description>\n {8}<code></code>\n {8}<arret></arret>\n {8}<ligne></ligne>\n {8}<ligne_nom></ligne_nom>\n {8}<sens></sens>\n {8}<vers></vers>\n {8}<couleur>#000000</couleur>\n {6}</description>".toRegex(), "")

        val res: ListeHorairesDTO = serializer.read(ListeHorairesDTO::class.java, cleanResult) ?: throw DataProviderException("Service returned invalid data")

        checkForErrors(res.erreur)

        return res.horaires.filter {
            horaire ->
            horaire.description != null && stops.any {
                stop ->
                stop.id == horaire.description?.code
                        && stop.line.id == horaire.description!!.ligne
                        && stop.line.direction.id == horaire.description!!.sens
            }
        }.map {
            horaire ->
            StopSchedule(
                    // Retrieve the stop corresponding to this schedule in the list that was passed
                    // to the API, so we can match them
                    stop = stops.filter { stop ->
                        stop.id == horaire.description?.code
                                && stop.line.id == horaire.description!!.ligne
                                && stop.line.direction.id == horaire.description!!.sens
                    }.first(),
                    // Parse the bus schedules
                    schedules = horaire.passages.map { passage ->
                        ScheduledArrival(
                                scheduleTime = passage.duree!!.getNextDateForTime(),
                                direction = passage.destination!!.smartCapitalize()
                        )
                    },
                    // Retrieve the traffic message(s) for this bus stop
                    trafficMessages = horaire.messages.filter { it.titre != null }.map { message ->
                        StopTrafficMessage(
                                title = message.titre!!.trim(),
                                body = message.texte?.trim()!!.replace("  ", " ")
                        )
                    }.distinct()
            )
        }
    }

    /**
     * Checks if there are outdated stops amongst those in the database,
     * by comparing them to a list of schedules returned by the API.
     */
    @Throws(DataProviderException::class)
    override fun checkForOutdatedStops(stops: List<Stop>, schedules: List<StopSchedule>): Int {
        if (stops.size == schedules.size) {
            return 0
        }

        return stops.fold(0) {
            outdated, stop ->

            if (stop.reference == null || schedules.none { schedule -> schedule.stop.id == stop.id }) {
                stop.isOutdated = true
                outdated + 1
            } else {
                outdated
            }
        }
    }

    /**
     * Checks the DTO returned by the API for eventual server-side errors.
     * Throws an exception if one is encountered.
     */
    @Throws(DataProviderException::class)
    private fun checkForErrors(error: ErreurDTO?, message: MessageDTO? = null) {
        if (error?.code != "000") {
            throw DataProviderException(errorCode = error!!.code, message = error.message)
        }

        if (message != null && message.bloquant && message.titre != null) {
            throw BlockingMessageException(message.titre!!, message.texte)
        }
    }

    /**
     * Fetches the current global traffic alert message.
     */
    val globalTrafficAlert: TrafficAlert?
        get() {
            try {
                val source = http.requestWebPage(PRE_HOME_URL, useCaches = true)

                if (!source.isEmpty()) {
                    val obj = JSONTokener(source).nextValue() as JSONObject

                    if (obj.has("alerte")) {
                        val alert = obj.getJSONObject("alerte")
                        return TrafficAlert(
                                alert.getInt("id_alerte"),
                                alert.getString("libelle_alerte").trim { it <= ' ' }.replace(" {2}".toRegex(), " - "),
                                alert.getString("url_alerte"))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }

            return null
        }

    /**
     * Gets the list of the supported bus networks.
     */
    val networksList: Map<Int, String>
        get() {
            return mapOf(
                    Pair(105, "Le Mans"),
                    Pair(117, "Pau"),
                    Pair(120, "Soissons"),
                    Pair(135, "Aix-en-Provence"),
                    Pair(147, "Caen"),
                    Pair(217, "Dijon"),
                    Pair(297, "Brest"),
                    Pair(402, "Pau-Agen"),
                    Pair(416, "Blois"),
                    Pair(422, "Saint-Étienne"),
                    Pair(440, "Nantes"),
                    Pair(457, "Montargis"),
                    Pair(497, "Angers"),
                    Pair(691, "Macon-Villefranche"),
                    Pair(910, "Épinay-sur-Orge"),
                    Pair(999, "Rennes")
            )
        }

    companion object {

        val TAG = TimeoRequestHandler::class.java.simpleName!!

        val DEFAULT_NETWORK_CODE = 147

        val API_BASE_URL = "http://timeo3.keolis.com/relais/"
        val PRE_HOME_URL = "http://twisto.fr/module/mobile/App2014/utils/getPreHome.php"

        /**
         * Returns the API endpoint for a given network code.
         *
         * @param networkCode the code for the city's bus network
         * @return the name of the page that has to be called for this specific network
         */
        private fun getEndpointUrl(networkCode: Int): String {
            return "$API_BASE_URL$networkCode.php"
        }
    }

}

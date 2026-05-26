package com.iosync.app.data.network

import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.model.StateControlCommand
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface targeting the ioBroker Simple-API adapter
 * (ioBroker.simple-api) and optionally a Home Assistant REST API.
 *
 * ioBroker Simple-API base URL: http://<host>:8087
 * Home Assistant base URL:      http://<host>:8123/api
 */
interface ApiService {

    /**
     * Returns all states matching an optional pattern.
     * ioBroker Simple-API: GET /getStates?pattern=*
     */
    @GET("getStates")
    suspend fun getAllStates(
        @Query("pattern") pattern: String = "*",
        @Query("prettyPrint") prettyPrint: Boolean = false
    ): Response<List<SmartHomeState>>

    /**
     * Returns a single state by its object ID.
     * ioBroker Simple-API: GET /getState?id=<id>
     */
    @GET("getState")
    suspend fun getState(
        @Query("id") id: String
    ): Response<SmartHomeState>

    /**
     * Sets a state value via GET (ioBroker Simple-API style).
     * ioBroker Simple-API: GET /setState?id=<id>&value=<value>
     */
    @GET("setState")
    suspend fun setStateGet(
        @Query("id") id: String,
        @Query("value") value: String
    ): Response<Unit>

    /**
     * Sets a state value via POST body.
     */
    @POST("setState")
    suspend fun setStatePost(
        @Body command: StateControlCommand
    ): Response<Unit>

    /**
     * Returns all states grouped by a given enum type (e.g. rooms, functions).
     * ioBroker Simple-API: GET /getStates?pattern=enum.rooms.*
     */
    @GET("getStates")
    suspend fun getStatesForRoom(
        @Query("pattern") roomPattern: String
    ): Response<List<SmartHomeState>>

    /**
     * Home Assistant: GET /api/states
     * Requires Authorization header set in the OkHttp interceptor.
     */
    @GET("states")
    suspend fun getHomeAssistantStates(): Response<List<SmartHomeState>>

    /**
     * Home Assistant: GET /api/states/<entity_id>
     */
    @GET("states/{entityId}")
    suspend fun getHomeAssistantState(
        @Path("entityId") entityId: String
    ): Response<SmartHomeState>

    /**
     * Checks whether the backend is reachable.
     * ioBroker Simple-API: GET /version
     */
    @GET("version")
    suspend fun checkHealth(): Response<Map<String, String>>
}

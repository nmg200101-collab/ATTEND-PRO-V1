package com.attendpro.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** V125 isolated remote employee administration client. Does not touch pairing/BLE/GPS. */
object ReceiverEmployeeAdminClient {
    data class Employee(
        val employeeId:String,
        val employeeName:String,
        val branchId:String,
        val lastSeenAt:Long,
        val enabled:Boolean,
        val pendingLink:Boolean,
        val pendingCommand:Boolean=false,
        val pendingAction:String=""
    )

    fun list(serverUrl:String, receiverId:String, secret:String):Result<List<Employee>> = runCatching {
        val o=post(serverUrl,"/api/v1/monitor/employees/manage-list",JSONObject().apply{put("receiverId",receiverId);put("secret",secret)})
        val a=o.optJSONArray("employees")?:JSONArray()
        (0 until a.length()).map { i ->
            val x=a.getJSONObject(i)
            Employee(
                x.optString("employeeId"),
                x.optString("employeeName"),
                x.optString("branchId","MAIN"),
                x.optLong("lastSeenAt",0L),
                x.optBoolean("enabled",true),
                x.optBoolean("pendingLink",false),
                x.optBoolean("pendingCommand",false),
                x.optString("pendingAction","")
            )
        }
    }
    fun add(serverUrl:String,receiverId:String,secret:String,id:String,name:String,branch:String)=command(serverUrl,"/api/v1/monitor/employees/add",receiverId,secret,id,name,branch,null)
    fun update(serverUrl:String,receiverId:String,secret:String,id:String,name:String,branch:String)=command(serverUrl,"/api/v1/monitor/employees/update",receiverId,secret,id,name,branch,null)
    fun setEnabled(serverUrl:String,receiverId:String,secret:String,id:String,enabled:Boolean)=command(serverUrl,"/api/v1/monitor/employees/status",receiverId,secret,id,"","",enabled)

    private fun command(url:String,path:String,rid:String,secret:String,id:String,name:String,branch:String,enabled:Boolean?):Result<String> = runCatching {
        val body=JSONObject().apply{
            put("receiverId",rid)
            put("secret",secret)
            put("employeeId",id)
            if(name.isNotBlank())put("employeeName",name)
            if(branch.isNotBlank())put("branchId",branch)
            if(enabled!=null)put("enabled",enabled)
        }
        post(url,path,body).optString("commandId")
    }
    private fun post(base:String,path:String,body:JSONObject):JSONObject {
        require(base.startsWith("https://")){"HTTPS required"}
        val response = ResilientHttp.execute(
            base.trimEnd('/') + path,
            "POST",
            mapOf("Content-Type" to "application/json; charset=utf-8", "Accept" to "application/json"),
            body.toString(),
            connectTimeoutMs = 12_000,
            readTimeoutMs = 15_000
        )
        val code = response.code
        val text = response.body
        if(code !in 200..299) throw IllegalStateException(runCatching{JSONObject(text).optString("error",text)}.getOrDefault(text))
        return if(text.isBlank()) JSONObject() else JSONObject(text)
    }
}

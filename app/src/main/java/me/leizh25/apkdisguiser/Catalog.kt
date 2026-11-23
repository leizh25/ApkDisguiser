package me.leizh25.apkdisguiser

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

internal data class CatalogApp(val id: String, val name: String, val iconUrl: String = "", val versionName: String = "")

internal class Catalog {
    private val guid = UUID.randomUUID().toString()

    fun search(keyword: String): List<CatalogApp> {
        val request = JSONObject("""{
            "head":{"cmd":"dc_pcyyb_official","authInfo":{"businessId":"AuthName"},
              "deviceInfo":{"platformType":2,"platform":1},"hostAppInfo":{"scene":"search_result"},
              "expSceneIds":""},
            "body":{"bid":"yybhome","offset":0,"size":20,"preview":false,
              "listS":{"region":{"repStr":["CN"]}},"layout":"yybn_search_result_list"}
        }""".trimIndent())
        request.getJSONObject("head").put("userInfo", JSONObject().put("guid", guid))
        request.getJSONObject("body").getJSONObject("listS")
            .put("keyword", JSONObject().put("repStr", JSONArray().put(keyword)))
        val connection = URL("https://yybadaccess.3g.qq.com/v2/dc_pcyyb_official")
            .openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain;charset=UTF-8")
            connection.setRequestProperty("Origin", "https://sj.qq.com")
            connection.setRequestProperty("Referer", "https://sj.qq.com/")
            connection.outputStream.use { it.write(request.toString().toByteArray()) }
            val response = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            check(response.getInt("ret") == 0)
            val components = response.getJSONObject("data").optJSONArray("components") ?: JSONArray()
            return buildList {
                for (i in 0 until components.length()) {
                    val items = components.optJSONObject(i)?.optJSONObject("data")
                        ?.optJSONArray("itemData") ?: continue
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j) ?: continue
                        val id = item.optString("pkg_name")
                        val name = item.optString("name").trim()
                        if (id.length <= 223 && packagePattern.matches(id) && name.isNotBlank()) {
                            add(CatalogApp(id, name, item.optString("icon"), item.optString("version_name").trim()))
                        }
                    }
                }
            }.distinctBy { it.id }
        } finally {
            connection.disconnect()
        }
    }
}

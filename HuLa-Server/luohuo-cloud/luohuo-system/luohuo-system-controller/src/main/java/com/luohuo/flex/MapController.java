package com.luohuo.flex;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.luohuo.basic.base.R;
import com.luohuo.flex.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

@RestController
@RequestMapping("/anyTenant/map")
@Tag(name = "腾讯地图代理")
@RequiredArgsConstructor
public class MapController {

    private final SysConfigService sysConfigService;
    @Resource(name = "restTemplate")
    private RestTemplate restTemplate;

    @GetMapping("/coord/translate")
    @Operation(summary = "坐标转换 WGS84->GCJ-02")
    public R<JSONObject> coordTranslate(@RequestParam("lat") double lat,
                                        @RequestParam("lng") double lng) {
        String key = sysConfigService.get("tencentMapKey");
        String url = "https://apis.map.qq.com/ws/coord/v1/translate?locations=" + lat + "," + lng + "&type=1&key=" + key;
        String resp = restTemplate.getForObject(url, String.class);
        JSONObject json = JSON.parseObject(resp == null ? "{}" : resp);
        if (json.getIntValue("status") == 0) {
            var arr = json.getJSONArray("locations");
            var loc = arr != null && !arr.isEmpty() ? arr.getJSONObject(0) : new JSONObject();
            JSONObject data = new JSONObject();
            data.put("lat", loc.getDouble("lat"));
            data.put("lng", loc.getDouble("lng"));
            return R.success(data);
        }
        JSONObject err = new JSONObject();
        err.put("status", json.getIntValue("status"));
        err.put("message", json.getString("message"));
        return R.success(err);
    }

    @GetMapping("/geocoder/reverse")
    @Operation(summary = "逆地理编码")
    public R<JSONObject> reverseGeocode(@RequestParam("lat") double lat,
                                        @RequestParam("lng") double lng) {
        String key = sysConfigService.get("tencentMapKey");
        String url = "https://apis.map.qq.com/ws/geocoder/v1/?location=" + lat + "," + lng + "&key=" + key + "&get_poi=1";
        String resp = restTemplate.getForObject(url, String.class);
        JSONObject json = JSON.parseObject(resp == null ? "{}" : resp);
        if (json.getIntValue("status") == 0) {
            return R.success(json.getJSONObject("result"));
        }
        JSONObject err = new JSONObject();
        err.put("status", json.getIntValue("status"));
        err.put("message", json.getString("message"));
        return R.success(err);
    }

    @GetMapping(value = "/static", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "静态地图图片")
    public R<JSONObject> staticMap(@RequestParam("lat") double lat,
                                   @RequestParam("lng") double lng,
                                   @RequestParam(value = "zoom", required = false, defaultValue = "18") int zoom,
                                   @RequestParam(value = "width", required = false, defaultValue = "600") int width,
                                   @RequestParam(value = "height", required = false, defaultValue = "400") int height) {
        String key = sysConfigService.get("tencentMapKey");
        String size = width + "*" + height;
        String url = "https://apis.map.qq.com/ws/staticmap/v2/?center=" + lat + "," + lng + "&zoom=" + zoom + "&size=" + size + "&format=png&key=" + key + "&markers=size:large|color:blue|label:A|" + lat + "," + lng;
        byte[] bytes = restTemplate.getForObject(url, byte[].class);
        String base64 = bytes == null ? "" : Base64.getEncoder().encodeToString(bytes);
        JSONObject data = new JSONObject();
        data.put("dataUrl", StringUtils.hasText(base64) ? ("data:image/png;base64," + base64) : "");
        return R.success(data);
    }
}

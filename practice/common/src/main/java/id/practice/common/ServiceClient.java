package id.practice.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class ServiceClient {
    private final RestTemplate http;
    private final String key;
    public ServiceClient(RestTemplate http, @Value("${app.internal-key}") String key) { this.http = http; this.key = key; }
    public <T> T get(String url, Class<T> type) { return request(url, HttpMethod.GET, null, type); }
    public <T> T post(String url, Object body, Class<T> type) { return request(url, HttpMethod.POST, body, type); }
    private <T> T request(String url, HttpMethod method, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders(); headers.set("X-Internal-Key", key);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try { return http.exchange(url, method, new HttpEntity<>(body, headers), type).getBody(); }
        catch (HttpClientErrorException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) throw ApiException.missing("Data pada service terkait tidak ditemukan");
            if (status == HttpStatus.CONFLICT) throw ApiException.conflict("Stok tidak cukup atau data pada service terkait berkonflik");
            if (status == HttpStatus.BAD_REQUEST) throw ApiException.invalid("Permintaan ditolak service terkait");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service terkait tidak dapat diakses");
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service terkait belum tersedia; coba kembali");
        }
    }
}

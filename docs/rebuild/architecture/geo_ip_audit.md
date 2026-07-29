# Geo-IP fallback security and privacy audit

The sole network feature is an explicit, persisted opt-in whose default is `false`. Both callers
check that gate. Runtime acquisition skips all location work for fixed coordinates, then tries
Android last-known and current fixes before geo-IP. The user-triggered **Use current location** action
tries an active Android fix first and can call geo-IP only after that fails.

## Transport and endpoint trust

- The fixed endpoint is `https://ipwho.is/`. App-wide cleartext is disabled. The client requires an
  `HttpsURLConnection`, disables redirects, and accepts only HTTP 200, so neither downgrade nor
  cross-host redirect can move the public-IP disclosure.
- TLS uses Android's platform trust store and hostname verification. There is deliberately no
  certificate pinning: pin expiry/rotation could disable a best-effort feature, while a compromised
  public CA could observe or alter only approximate coordinates. Strict parsing/range checks and the
  fail-closed consumer boundary prevent such a response from corrupting circadian state.
- Connect and read timeouts remain 30 seconds each for Tasker parity. Cancellation disconnects the
  blocking connection and is rethrown rather than converted to an ordinary network failure.

## Input and state bounds

- Responses are limited to 16 KiB, including chunked/unknown-length bodies; an oversized declared
  content length is rejected before reading. JSON must be a real object with Boolean `success:true`
  and numeric `latitude`/`longitude`. Coordinates must be finite and within latitude `[-90, 90]` and
  longitude `[-180, 180]`; `(0,0)` remains rejected.
- The runtime validates every acquired snapshot again before applying or persisting it. Network,
  parse, cancellation-independent lookup failure, and invalid data publish nothing: existing cached
  coordinates remain intact, or the pipeline continues with its documented default windows.

## Frequency, disclosure, logging, and persistence

Automatic acquisition is guarded and its attempted day is persisted whether it succeeds or fails; a
successful coordinate is also stored in app-private DataStore for reuse after process death. This prevents
sensor-driven pipeline evaluations from repeatedly spending battery or disclosing the public IP during
an outage, and prevents process restarts from making more than one automatic attempt that day. The 30-second
timeouts bound an attempt. The interactive action can make one additional
request per tap after its 20-second active local fix attempt. No background polling loop exists.

The request necessarily reveals the user's public IP to ipwho.is and the TLS/network providers; it
sends no app identifier or stored coordinates. Neither failures nor coordinates are logged. Successful
coordinates and their acquisition day are persisted app-private, excluded from backup/device transfer,
and reused to avoid unnecessary requests. The UI help, README privacy section, and store description
name ipwho.is, HTTPS, the opt-in/default-off gate, ordering, daily automatic behavior, interactive
requests, and on-device coordinate persistence. With those additions the disclosure is adequate for
retaining this optional feature.

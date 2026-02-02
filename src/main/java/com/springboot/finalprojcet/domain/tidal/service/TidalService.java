package com.springboot.finalprojcet.domain.tidal.service;

import com.springboot.finalprojcet.domain.tidal.dto.*;

public interface TidalService {
    TidalLoginUrlResponse getLoginUrl(String visitorId, String origin);

    TidalExchangeResponse exchangeCode(TidalExchangeRequest request, String origin);

    TidalAuthStatusResponse getAuthStatus(String visitorId);

    void logout(String visitorId);

    TidalPlaylistResponse getUserPlaylists(String visitorId);

    TidalImportResponse importPlaylist(TidalImportRequest request);

    TidalSyncResponse syncTidal(Long userId, TidalSyncRequest request);

    TidalDeviceAuthResponse initDeviceAuth();

    TidalTokenPollResponse pollToken(TidalTokenPollRequest request);

    TidalFeaturedResponse getFeatured();

    Object getPlaylistTracks(String id, String countryCode, int limit, int offset);
}

package com.wk.search.service;

import java.io.IOException;

public interface SearchHouseService {
    void search(Integer page) throws IOException;

    String switchStatus(String id);
}

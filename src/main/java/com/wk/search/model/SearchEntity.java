package com.wk.search.model;

import java.io.Serializable;
import java.util.Date;


public class SearchEntity implements Serializable {
    private static final long serialVersionUID = 6431478674204338261L;
    private String id;
    private Float scope;
    private String keywordsList;
    private String blackWordsList;
    private String receiver;

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    private Date updateTime;

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Float getScope() {
        return scope;
    }

    public void setScope(Float scope) {
        this.scope = scope;
    }

    public String getKeywordsList() {
        return keywordsList;
    }

    public void setKeywordsList(String keywordsList) {
        this.keywordsList = keywordsList;
    }

    public String getBlackWordsList() {
        return blackWordsList;
    }

    public void setBlackWordsList(String blackWordsList) {
        this.blackWordsList = blackWordsList;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }
}

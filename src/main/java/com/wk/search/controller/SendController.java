package com.wk.search.controller;

import com.wk.search.service.SearchHouseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@EnableScheduling
public class SendController {

    @Autowired
    private SearchHouseService searchHouseService;

    @GetMapping(value = "/send/{page}")
    @ResponseBody
//    @Scheduled(cron = "0 0 0/1 * * ?") //每小时执行一次
    public String sendEmail(@PathVariable int page) {
        try {
            searchHouseService.search(page);
            return "请求成功!";
        } catch (MailException | IOException ex) {
            System.err.println(ex.getMessage());
            return "请求失败!";
        }
    }

    @GetMapping(value = "/send")
    @ResponseBody
//    @Scheduled(cron = "0 0 0/1 * * ?") //每小时执行一次
    public String sendEmail() {
        try {
            searchHouseService.search(null);
            return "请求成功!";
        } catch (MailException | IOException ex) {
            System.err.println(ex.getMessage());
            return "请求失败!";
        }
    }

    @GetMapping(value = "/switch/{email}")
    public String switchStatus(@PathVariable String email) {
        return searchHouseService.switchStatus(email);
    }

}

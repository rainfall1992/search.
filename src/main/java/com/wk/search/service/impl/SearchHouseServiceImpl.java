package com.wk.search.service.impl;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.arronlong.httpclientutil.HttpClientUtil;
import com.arronlong.httpclientutil.common.HttpConfig;
import com.arronlong.httpclientutil.common.HttpCookies;
import com.arronlong.httpclientutil.common.HttpHeader;
import com.arronlong.httpclientutil.common.HttpResult;
import com.wk.search.mapper.SearchMapper;
import com.wk.search.model.SearchEntity;
import com.wk.search.service.SearchHouseService;
import com.wk.search.utils.MailSenderUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchHouseServiceImpl implements SearchHouseService {
    @Resource
    private MailSenderUtil mailSenderUtil;
    @Resource
    private SearchMapper searchMapper;
    @Value("${spring.search.searchUrls}")
    private String searchUrls;

    @Override
    public void search(Integer page) throws IOException {
        List<SearchEntity> list = searchMapper.getSearchInfo();
        for (SearchEntity entity : list) {
            send1(entity, page);
        }
    }

    @Override
    public String switchStatus(String email) {
        Integer status = searchMapper.statusByEmail(email);
        if (status != null) {
            if (status == 0) {
                searchMapper.switchStatus(email, 1);
                return "已开启提醒";
            }
            if (status == 1) {
                searchMapper.switchStatus(email, 0);
                return "已关闭提醒";
            }
        }
        return "收件人不存在";
    }

    private String[] getLines(String searchUrl, String keyword) throws Exception {
        // 配置Header
        Header[] headers = HttpHeader.custom()
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.120 Safari/537.36")
                .accept("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3")
                .connection("keep-alive")
                .cookie("_ga=GA1.2.1565475140.1570672684; ll=\"108296\"; gr_user_id=a1daa0cd-61eb-4651-89e3-8dffd8a8f7c1; __utmv=30149280.23353; bid=EwNSpsLTGoQ; __utmc=30149280; __utmz=30149280.1637565844.19.9.utmcsr=google|utmccn=(organic)|utmcmd=organic|utmctr=(not%20provided); dbcl2=\"233539861:sTxQt/6wp1Q\"; ck=-B9H; push_noty_num=0; push_doumail_num=0; ct=y; ap_v=0,6.0; __utma=30149280.1565475140.1570672684.1639449246.1639463429.51; _pk_ref.100001.8cb4=%5B%22%22%2C%22%22%2C1639466082%2C%22https%3A%2F%2Fwww.google.com.hk%2F%22%5D; _pk_id.100001.8cb4=2156c3af81fe3ba0.1561965743.56.1639466082.1639463437.; _pk_ses.100001.8cb4=*")
                .build();
        HttpConfig config = HttpConfig.custom()
                .headers(headers)
                .timeout(3000)
                .url(searchUrl + keyword)
                .context(HttpCookies.custom().getContext());
        HttpResult respResult = HttpClientUtil.sendAndGetResp(config);
        String result = respResult.getResult();
        if (result.contains("403 Forbidden")) {
            log.info("403 Forbidden");
        }
        //获取网页源码
        return result.split("\\r?\\n");
    }

    private void send(SearchEntity searchInfo) throws IOException {
        Float scope = searchInfo.getScope();
        String receiver = searchInfo.getReceiver();
        String[] keywords = searchInfo.getKeywordsList().split(",");
        String[] blackWords = searchInfo.getBlackWordsList().split(",");
        StringBuilder validText = new StringBuilder();
        StringBuilder infoStr = new StringBuilder();
        String[] urlList = searchUrls.split(",");
        int year = Calendar.getInstance().get(Calendar.YEAR);
        try {
            for (String keyword : keywords) {
                for (String searchUrl : urlList) {
                    String[] lines = getLines(searchUrl, keyword);
                    //逐行读取网页源码
                    for (String line : lines) {
                        //找到有效数据
                        if (line.contains("group_topic_by_time") || (line.contains("\n") && line.endsWith("</a></td>"))) {
                            validText = new StringBuilder(line);
                        }
                        if (line.contains("td-time") && (line.contains(year + "") || line.contains(year - 1 + ""))) {
                            validText.append(line);
                            int startIndex = validText.indexOf(year + "-");
                            String dateStr = validText.substring(startIndex, validText.indexOf("\" nowrap="));
                            String urlStr = validText.substring(validText.indexOf("http"), validText.indexOf("\" onclick"));
                            String titleStr = validText.substring(validText.indexOf("title=\"") + 7, validText.lastIndexOf("<td class=\"td-time\""));
                            if (titleStr.contains("</a></td>")) {
                                titleStr = titleStr.substring(0, titleStr.indexOf("</a></td>"));
                            }
                            if (titleStr.contains("\">")) {
                                titleStr = titleStr.substring(0, titleStr.indexOf("\">"));
                            }
                            String info = "[" + keyword + "]\r\n" + "标题：" + titleStr + "\r\n" + dateStr + "\r\n" + urlStr + "\r\n";
                            Date parse = DateUtil.parse(dateStr, "yyyy-MM-dd HH:mm:ss");
                            float time = (new Date().getTime() - parse.getTime()) / 1000F / 60F / 60F;
                            boolean blackWordFlag = true;
                            for (String blackWord : blackWords) {
                                if (!StrUtil.isEmpty(blackWord) && titleStr.contains(blackWord)) {
                                    blackWordFlag = false;
                                    break;
                                }
                            }
                            if (blackWordFlag && time < scope
                                    && !infoStr.toString().contains(titleStr.trim())
                                    && ((titleStr.length() <= 9) || !(infoStr.toString().contains(titleStr.substring(0, 8))))
                                    && ((titleStr.length() <= 6) || !(infoStr.toString().contains(titleStr.substring(titleStr.length() - 5, titleStr.length() - 1))))
                                    && !titleStr.contains("求租")
                                    && (titleStr.contains("房") || titleStr.contains("租") || titleStr.contains("间") || titleStr.contains("厅")
                                    || titleStr.contains("室") || titleStr.contains("住"))) {
                                //如果数据符合上述条件 拼接字符串
                                infoStr.append(info).append("\r\n");
                            }
                        }
                    }
                }
            }
            // log.info("\r\n" + "[最终正文]:" + "\r\n" + infoStr);
            if (infoStr.length() > 0) {
                String str = "====================================" + "\r\n" + "检索范围: " + scope + "小时内" + "\r\n" + "关键词: " + Arrays.toString(keywords) + "\r\n" + "====================================" + "\r\n";
                mailSenderUtil.sendSimpleMail(receiver, "豆瓣租房-发现新房源", str + infoStr.toString());
                log.info("{} 邮件发送成功!", receiver);
            } else {
                log.info("未找到合适信息");
            }
        } catch (Exception e) {
            log.info("邮件发送失败!");
            e.printStackTrace();
        }
    }

    private void send1(SearchEntity searchInfo, Integer page) throws IOException {
        Float scope = searchInfo.getScope();
        String receiver = "371682060@qq.com,1052245541@qq.com";
//        String receiver = searchInfo.getReceiver();
        String[] keyWords = {"静安", "长宁", "徐汇", "镇坪路", "中潭路", "中山公园", "江苏路", "娄山关", "南京东路", "南京西路", "人民广场", "西藏南路", "金沙江路",
                "宜山路", "上海体育场", "上海体育馆", "打浦桥", "鲁班路", "虹桥路", "延安西路", "曹杨路", "上海火车站", "大木桥路", "东安路", "漕宝路",
                "龙漕路", "龙华", "嘉善路", "汉中路", "曲阜路", "隆德路", "新天地", "马当路", "淮海中路", "自然博物馆", "交通大学", "真如", "枫桥路",
                "上海游泳馆", "云锦路", "龙耀路", "龙柏新村", "上海动物园", "龙溪路", "水城路", "伊犁路", "宋园路", "上海图书馆",
                "老西门", "豫园", "天潼路", "四川北路", "陆家浜路", "大世界", "石龙路", "上海南站", "漕溪路", "衡山路"};
        String[] blackWords = {"合租", "室友", "求", "随时", "拎包", "16号", "17号", "18号", "浦江", "5/", "/16", "/17", "/18", "自如", "九亭", "泗泾", "漕河泾",
                "松江", "卧", "两房", "两室", "两厅", "loft", "LOFT", "公寓", "直达", "可达", "2室", "单间", "每户", "女生", "男生", "房间", "个人",
                "低至", "半小时", "售", "合用", "通勤", "分钟", "小时", "诚意", "房源", "给钱"};
//        String[] keywords = searchInfo.getKeywordsList().split("，");
//        String[] blackWords = searchInfo.getBlackWordsList().split("，");
        StringBuilder validText = new StringBuilder();
        StringBuilder infoStr = new StringBuilder();
        String[] urlList = searchUrls.split(",");
//        int year = Calendar.getInstance().get(Calendar.YEAR);
        Date updateTime = searchInfo.getUpdateTime();
        String updated = DateUtil.format(updateTime, "MM-dd HH:mm");
        String created = DateUtil.format(new Date(), "MM-dd HH:mm");
        try {
            long t1 = System.currentTimeMillis();
            for (String searchUrl : urlList) {
                boolean forFlag = true;
                int i = 0;
                while (forFlag || (Objects.nonNull(page) && i <= page)) {
                    String[] lines = getLines1(searchUrl + i);
                    //逐行读取网页源码
                    List<String> list = Arrays.asList(lines);
                    List<String> topics = list.stream().filter(v -> v.contains("https://www.douban.com/group/topic/")).collect(Collectors.toList());
                    List<String> times = list.stream().filter(v -> v.contains("class=\"time\"")).collect(Collectors.toList());
                    for (String topic : topics) {
                        boolean flag = true;
                        validText = new StringBuilder(topic);
                        String titleStr = validText.substring(validText.indexOf("title=\"") + 7, validText.indexOf("\" class") < 0 ? validText.length() : validText.indexOf("\" class"));
//                            System.out.println(titleStr);
                        for (String bw : blackWords) {
                            if (titleStr.contains(bw)) {
                                flag = false;
                                break;
                            }
                        }
                        if (flag) {
                            for (String kw : keyWords) {
                                if (titleStr.contains(kw)) {
                                    String urlStr = validText.substring(validText.indexOf("www"), validText.indexOf("\" title"));
                                    String info = titleStr + "\r\n" + urlStr;
                                    infoStr.append(info).append("\r\n");
                                    break;
                                }
                            }
                        }
                    }
                    for (String time : times) {
                        time = time.substring(time.indexOf("time") + 6, time.indexOf("</td>"));
                        if (time.compareTo(updated) < 0) {
                            forFlag = false;
                            break;
                        }
                    }
                    i += 30;
                }
            }
            long t2 = System.currentTimeMillis();
            log.info("总耗时：{} ms", t2 - t1);
            searchMapper.updateTime();
            // log.info("\r\n" + "[最终正文]:" + "\r\n" + infoStr);
            if (infoStr.length() > 0) {
                String subject = "豆瓣租房 " + updated + " 至 " + created;
                String[] receivers = receiver.split(",");
                for (String r : receivers) {
                    mailSenderUtil.sendSimpleMail(r, subject, infoStr.toString());
                    log.info("{} 邮件发送成功!", r);
                }
            } else {
                log.info("未找到合适信息");
            }
        } catch (Exception e) {
            log.info("邮件发送失败!");
            e.printStackTrace();
        }
    }

    private String[] getLines1(String searchUrl) throws Exception {
        Header[] headers = HttpHeader.custom()
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.120 Safari/537.36")
                .accept("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3")
                .connection("keep-alive")
                .cookie("_ga=GA1.2.1565475140.1570672684; ll=\"108296\"; gr_user_id=a1daa0cd-61eb-4651-89e3-8dffd8a8f7c1; __utmv=30149280.23353; bid=EwNSpsLTGoQ; __utmc=30149280; __utmz=30149280.1637565844.19.9.utmcsr=google|utmccn=(organic)|utmcmd=organic|utmctr=(not%20provided); dbcl2=\"233539861:sTxQt/6wp1Q\"; ck=-B9H; push_noty_num=0; push_doumail_num=0; ct=y; ap_v=0,6.0; __utma=30149280.1565475140.1570672684.1639449246.1639463429.51; _pk_ref.100001.8cb4=%5B%22%22%2C%22%22%2C1639466082%2C%22https%3A%2F%2Fwww.google.com.hk%2F%22%5D; _pk_id.100001.8cb4=2156c3af81fe3ba0.1561965743.56.1639466082.1639463437.; _pk_ses.100001.8cb4=*")
                .build();
        HttpConfig config = HttpConfig.custom()
                .headers(headers)
                .timeout(3000)
                .url(searchUrl)
                .context(HttpCookies.custom().getContext());
        HttpResult respResult = HttpClientUtil.sendAndGetResp(config);
        String result = respResult.getResult();
        if (result.contains("403 Forbidden")) {
            log.info("403 Forbidden");
        }
        return result.split("\\r?\\n");
    }
}

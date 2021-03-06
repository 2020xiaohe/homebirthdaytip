package com.home.homebirthdaytip.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.home.homebirthdaytip.common.Constants;
import com.home.homebirthdaytip.common.utils.DateUtils;
import com.home.homebirthdaytip.common.utils.threads.SendSms;
import com.home.homebirthdaytip.domain.CCommonPush;
import com.home.homebirthdaytip.domain.CCommonPushUsers;
import com.home.homebirthdaytip.mapper.CCommonPushMapper;
import com.home.homebirthdaytip.service.CCommonPushAccountRetativeIdService;
import com.home.homebirthdaytip.service.CCommonPushService;
import com.home.homebirthdaytip.service.CCommonPushUsersService;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateData;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 *
 */
@Service
public class CCommonPushServiceImpl extends ServiceImpl<CCommonPushMapper, CCommonPush>
    implements CCommonPushService{
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * ?????????????????????qq??????
     */
    @Value("${spring.mail.from}")
    private String from;

    @Resource
    TemplateEngine templateEngine;

    @Resource
    private JavaMailSender mailSender;


    /**
     * AppId
     */
    @Value("${weixin.appId}")
    private String appId;

    /**
     * ??????
     */
    @Value("${weixin.appSecret}")
    private String appSecret;

    /**
     * ????????????
     */
    @Value("${duanxin.accessKeyID}")
    private String accessKeyID;

    /**
     * ????????????
     */
    @Value("${duanxin.accessKeySecret}")
    private String accessKeySecret;


    @Resource
    private CCommonPushMapper cCommonPushMapper;

    @Autowired
    private CCommonPushService cCommonPushService;
    @Autowired
    private CCommonPushUsersService cCommonPushUsersService;
    @Autowired
    private CCommonPushAccountRetativeIdService cCommonPushAccountRetativeIdService;

    @Override
    public void sendHtmlMail(String threadName, Integer id) {
        long startTime=System.currentTimeMillis();
//        logger.info("*****??????????????????"+ DateUtils.formatDate(new Date(),DateUtils.PATTERN_24TIME) +"?????????c_common_push??????"+id+"?????????????????????");
        CCommonPush c = cCommonPushMapper.selectById(id);
        //??????????????????
        Context context = new Context();
        context.setVariable("name", "?????????,"+c.getPushAccountName()+"!");
        context.setVariable("article", c.getPushArticle());
        context.setVariable("threadName", "??????????????????"+threadName);

        String headPic = "1.jpg";
        String TemplateName="emailTemplate";
        if(c.getPushTemplateId()!=null && c.getPushTemplateId()!=""){
            headPic = "";
            TemplateName="";
        }

        InputStream stream = this.getClass().getClassLoader().getResourceAsStream("static/images/"+headPic);
        InputStream stream2 = this.getClass().getClassLoader().getResourceAsStream("static/images/sign.png");
        File file1 = new File(headPic);
        File file2 = new File("sign.png");
        try {
            if(!file1.exists()){
                FileUtils.copyInputStreamToFile(stream, file1);
            }
            if(!file2.exists()){
                FileUtils.copyInputStreamToFile(stream2, file2);
            }
        } catch (IOException e) {
            logger.error("*****????????????????????????*****");
        }
        FileSystemResource img = new FileSystemResource(file1);
        FileSystemResource signImg = new FileSystemResource(file2);
        String process = templateEngine.process(TemplateName, context);

        //????????????
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper messageHelper;
        try {
            // ???????????????????????????????????????
            message.addHeader("X-Mailer","Microsoft Outlook Express 6.00.2900.2869");
            // ??????????????????
            c.setPushStartTime(new Date());
            messageHelper = new MimeMessageHelper(message, true);
            //???????????????
            messageHelper.setFrom(from);
            //???????????????
            messageHelper.setTo(c.getPushAccount());
            //????????????
            message.setSubject(c.getPushTheme());
            //???????????????html??????
            messageHelper.setText(process, true);
            messageHelper.addInline("p03", img);
            messageHelper.addInline("p04", signImg);
            //??????
            mailSender.send(message);
            //???????????????????????????????????????

            c.setPushEndTime(new Date());
            c.setPushStatus(Constants.PUSH_STATUS.yjfs.getIndex());
            cCommonPushMapper.updateById(c);
            long endTime=System.currentTimeMillis();
            logger.info("*****??????????????????"+ DateUtils.formatDate(new Date(),DateUtils.PATTERN_24TIME) +"?????????c_common_push??????"+id+"?????????????????????,?????????"+threadName+"?????????"+c.getPushAccountName()+".?????????"+(endTime-startTime)+"ms*****");
        } catch (MessagingException e) {
            logger.info("*****??????????????????"+DateUtils.formatDate(new Date(),DateUtils.PATTERN_24TIME)+"?????????c_common_push??????"+id+"?????????????????????,???"+threadName+"?????????"+c.getPushAccountName()+",????????????????????????????????????*****");
            c.setPushStatus(Constants.PUSH_STATUS.fsyc.getIndex());
            cCommonPushMapper.updateById(c);
        }
    }

    @Override
    public String sendWxMessage(String threadName, Integer id, WxMpService wxMpService) {
        CCommonPush c =cCommonPushMapper.selectById(id);
        if(!("").equals(c.getPushTemplateId()) && c.getPushTemplateId() !=null){
            String templateValue ="";
            //????????????????????????????????????
            String redictUrl ="";
            Map<String,String> map =new HashMap<>();
            String[] p=c.getPushTemplateParams().split(",");
            if (Constants.TEMPLATE_ENUM.tz.getIndex().equals(c.getPushTemplateId())){
                templateValue = Constants.TEMPLATE_ENUM.tz.getValue();
                redictUrl = Constants.TZ_REDICTURL;
                map.put("name",p[0]);
                map.put("things",p[1]);
                map.put("date",p[2]);
                map.put("address",p[3]);
            }else if(Constants.TEMPLATE_ENUM.srtx.getIndex().equals(c.getPushTemplateId())){
                templateValue = Constants.TEMPLATE_ENUM.srtx.getValue();
                redictUrl = Constants.SRTX_REDICTURL;
                map.put("name",p[0]);
                map.put("birthday",p[1]);
            }else if(Constants.TEMPLATE_ENUM.jtcysrtx.getIndex().equals(c.getPushTemplateId())){
                templateValue = Constants.TEMPLATE_ENUM.jtcysrtx.getValue();
                redictUrl = Constants.JTCYSRTX_REDICTURL;
                map.put("name_1",p[0]);
                map.put("birthday",p[1]);
                map.put("name_2",p[2]);
            }else if(Constants.TEMPLATE_ENUM.jrtx.getIndex().equals(c.getPushTemplateId())){
                templateValue = Constants.TEMPLATE_ENUM.jrtx.getValue();
                redictUrl = Constants.JRTX_REDICTURL;
                map.put("festival",p[0]);
                map.put("time",p[1]);
                map.put("remark",p[2]);
            }
            //??????????????????
            WxMpTemplateMessage templateMessage = WxMpTemplateMessage.builder()
                    .toUser(c.getPushAccount())//??????????????????openid
                    .templateId(templateValue)//??????id
                    .url(redictUrl)//????????????????????????????????????
                    .build();
            //3,?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            for(String key : map.keySet()){
                String value = map.get(key);
                templateMessage.addData(new WxMpTemplateData(key, value));
            }

            try {
                c.setPushStartTime(new Date());
                wxMpService.getTemplateMsgService().sendTemplateMsg(templateMessage);
                c.setPushEndTime(new Date());
                c.setPushStatus(Constants.PUSH_STATUS.yjfs.getIndex());
                cCommonPushMapper.updateById(c);
                logger.info("*****????????????????????????c_common_push??????"+id+"?????????????????????,?????????"+threadName+"?????????"+c.getPushAccountName()+"*****");
                return "????????????";
            } catch (Exception e) {
                logger.info("*****????????????????????????c_common_push??????"+id+"?????????????????????,???"+threadName+"?????????"+c.getPushAccountName()+",????????????????????????????????????*****");
                c.setPushStatus(Constants.PUSH_STATUS.fsyc.getIndex());
                cCommonPushMapper.updateById(c);
                return "????????????";
            }
        }else {
            return "??????id????????????";
        }
    }

    @Override
    public String sendMobileMessage(String threadName, Integer id) {
        //??????id??????????????????
        String result ="";
        CCommonPush c = cCommonPushMapper.selectById(id);
        String account =Constants.COUNTRY_CODE+c.getPushAccount();
        String[] accounts =account.split(",");
        String[] params = c.getPushTemplateParams().split(",");
        String tempId = Constants.DX_TEMPLATE_ENUM.getValueByIndex(c.getPushTemplateId());
        c.setPushStartTime(new Date());
        if(SendSms.push(accessKeyID,accessKeySecret,tempId,accounts,params)){
            result = "????????????";
            c.setPushStatus(Constants.PUSH_STATUS.yjfs.getIndex());
            logger.info("*****????????????????????????c_common_push??????" + id + "?????????????????????,?????????" + threadName + "?????????" + c.getPushAccountName() + "*****");
        }else{
            result = "????????????";
            c.setPushStatus(Constants.PUSH_STATUS.fsyc.getIndex());
            logger.info("*****????????????????????????c_common_push??????" + id + "?????????????????????,???" + threadName + "?????????" + c.getPushAccountName() + ",????????????????????????????????????*****");
        }
        c.setPushEndTime(new Date());
        cCommonPushMapper.updateById(c);
        return result;
    }

    @Override
    public List<Map<String, Object>> getEmailPushStatistics() {
        List<Map<String, Object>> result = cCommonPushMapper.getEmailTaskResults(Constants.SERVICE_TYPE.yj.getIndex());
        for (Map<String, Object> map:result) {
            if(Integer.parseInt(String.valueOf(map.get("name"))) == Constants.PUSH_STATUS.ddfs.getIndex()){
                map.put("name",Constants.PUSH_STATUS.ddfs.getName());
            }else if(Integer.parseInt(String.valueOf(map.get("name"))) == Constants.PUSH_STATUS.yjfs.getIndex()){
                map.put("name",Constants.PUSH_STATUS.yjfs.getName());
            }else if(Integer.parseInt(String.valueOf(map.get("name"))) == Constants.PUSH_STATUS.fsyc.getIndex()){
                map.put("name",Constants.PUSH_STATUS.fsyc.getName());
            }
            map.put("value",String.valueOf(map.get("value")));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getWxPushStatistics() {
        List<Map<String, Object>> result = cCommonPushMapper.getEmailTaskResults(Constants.SERVICE_TYPE.dx.getIndex());
        for (Map<String, Object> map:result) {
            if(Integer.parseInt(String.valueOf(map.get("name"))) == Constants.PUSH_STATUS.ddfs.getIndex()){
                map.put("name",Constants.PUSH_STATUS.ddfs.getName());
            }else if(Integer.parseInt(String.valueOf(map.get("name"))) == Constants.PUSH_STATUS.yjfs.getIndex()){
                map.put("name",Constants.PUSH_STATUS.yjfs.getName());
            }else if(Integer.parseInt(String.valueOf(map.get("name"))) == Constants.PUSH_STATUS.fsyc.getIndex()){
                map.put("name",Constants.PUSH_STATUS.fsyc.getName());
            }
            map.put("value",String.valueOf(map.get("value")));
        }
        return result;
    }

    @Override
    public Map<String, Object> getWeekPushCondition(String type) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String,Object>> list = cCommonPushMapper.getWeekPushCondition(Integer.parseInt(type));
        List<String> legList = new ArrayList<>();
        List<Integer> dataList = new ArrayList<>();
        for (Map<String,Object> map:list) {
            dataList.add(Integer.parseInt(String.valueOf(map.get("count"))));
            legList.add(String.valueOf(map.get("click_date")));
        }
        result.put("leg",legList);
        result.put("data",dataList);
        return result;

    }

    @Override
    public int countByPushTypeAndPushStatus(Integer pushType, Integer pushStatus) {
        return cCommonPushMapper.countByPushTypeAndPushStatus(pushType,pushStatus);
    }

    @Override
    public IPage<CCommonPush> getByPage(int pageNum, int pageSize, int pushType, int pushStatus) {
        Page<CCommonPush> page = new Page<>(pageNum, pageSize);
        IPage<CCommonPush> iPage = getBaseMapper().selectPage(page,
                new QueryWrapper<CCommonPush>().eq("push_type",pushType)
                                               .eq("push_status",pushStatus));
        return iPage;
    }

    @Override
    public List<CCommonPush> parsentExcelToEmails(MultipartFile myfile) {
        List<CCommonPush> list = new ArrayList<>();
        NumberFormat f=new DecimalFormat("############");
        f.setMaximumFractionDigits(0);
        try {
            /*excel????????????*/
            InputStream ins = myfile.getInputStream();

            // ??????excel?????????
            // ???????????????
            Workbook workbook = null;
            workbook = WorkbookFactory.create(ins);

            //HSSFWorkbook workbook = new HSSFWorkbook(ins);
            // ????????????????????????
            Sheet sheet = workbook.getSheetAt(0);

            //???????????????????????????
            int lastRowNum = sheet.getLastRowNum();

            /**
             * ?????????????????????
             */
            //????????????????????????
            for (int i = 2; i < lastRowNum + 1; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                } else {
                    if (row.getCell(1) != null && row.getCell(3) != null
                            && StringUtils.isNotBlank(String.valueOf(row.getCell(1)))
                            && StringUtils.isNotBlank(String.valueOf(row.getCell(3)))) {
                        CCommonPush s = new CCommonPush();
                        //??????
                        String email = row.getCell(1).toString();
                        //????????????
                        String theme = row.getCell(2).toString();
                        String article = row.getCell(3).toString();
                        if (row.getCell(0) != null) {
                            String name = row.getCell(0).toString();
                            s.setPushAccountName(name);
                        }

                        s.setPushType(Constants.SERVICE_TYPE.yj.getIndex());
                        s.setPushAccount(email);
                        s.setPushTheme(theme);
                        s.setPushArticle(article);
                        s.setPushStatus(Constants.PUSH_STATUS.ddfs.getIndex());
                        s.setPushCreateTime(new Date());
                        list.add(s);
                    } else {
//                        logger.info("????????????????????????????????????" + i + 1 + "??????????????????===>??????");
                        continue;
                    }
                }
            }
            int emails = list.size();
//            logger.info("?????????????????????????????????" + emails + "???");
            //???????????????sheet,??????4?????????
            // ????????????????????????
            Sheet sheet1 = workbook.getSheetAt(1);
            int lastRowNum1 = sheet1.getLastRowNum();
            for (int i = 4; i < lastRowNum1 + 1; i++) {
                Row row = sheet1.getRow(i);
                if (row == null) {
//                    logger.info("????????????????????????????????????"+i+1+"?????????===>??????");
                    continue;
                } else {
                    if (row.getCell(0) != null && row.getCell(1) != null && row.getCell(2) != null
                            && StringUtils.isNotBlank(String.valueOf(row.getCell(0))) && StringUtils.isNotBlank(String.valueOf(row.getCell(1)))
                            && StringUtils.isNotBlank(String.valueOf(row.getCell(2)))) {
                        String templateId = String.format("%.0f", row.getCell(0).getNumericCellValue());
                        String phoneNumber = f.format(row.getCell(1).getNumericCellValue());
                        String params = String.valueOf(row.getCell(2));
                        //??????????????????????????????
                        if (Pattern.matches(Constants.PHONE_NUM_CHECK, phoneNumber)) {
                            //??????????????????????????????
                            if (templateId.equals(Constants.DX_TEMPLATE_ENUM.tz.getIndex())
                                    || templateId.equals(Constants.DX_TEMPLATE_ENUM.xczf.getIndex())){
                                CCommonPush s = new CCommonPush();
                                s.setPushTemplateId(templateId);
                                s.setPushTemplateParams(params);
                                s.setPushAccount(phoneNumber);
                                s.setPushAccountName(params.split(",")[0]);
                                s.setPushType(Constants.SERVICE_TYPE.dx.getIndex());
                                s.setPushStatus(Constants.PUSH_STATUS.ddfs.getIndex());
                                s.setPushCreateTime(new Date());
                                //???????????????4???
                                if(templateId.equals(Constants.DX_TEMPLATE_ENUM.tz.getIndex()) && params.split(",").length == 4){
                                    s.setPushTheme("??????");
                                    list.add(s);
                                }
                                //??????????????????2???
                                if(templateId.equals(Constants.DX_TEMPLATE_ENUM.xczf.getIndex()) && params.split(",").length == 2){
                                    s.setPushTheme("????????????");
                                    list.add(s);
                                }
                                logger.info("????????????????????????==>???????????????" + i + 1 + "?????????");
                            }else {
                                logger.info("????????????????????????????????????" + i + 1 + "?????????Id????????????===>??????");
                                continue;
                            }
                        } else {
                            logger.info("????????????????????????????????????" + i + 1 + "???????????????===>??????");
                            continue;
                        }
                    } else {
//                        logger.info("????????????????????????????????????" + i + 1 + "???????????????????????????===>??????");
                        continue;
                    }
                }
            }
            int messages = 0;
            if(emails != 0){
                messages = list.size() - emails ;
            }else {
                messages = list.size();
            }
            logger.info("?????????????????????????????????"+emails+"???,?????????????????????" + messages + "???");
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return list;
    }

    @Override
    public void downloadExcelTemplate(String fileName, String downLoadName,
                                      HttpServletRequest request, HttpServletResponse response) throws Exception {
        long startTime=System.currentTimeMillis();
        InputStream inStream =this.getClass().getClassLoader().getResourceAsStream("static/doc/"+fileName);
        Workbook workbook = null;
        workbook = new XSSFWorkbook(inStream);
        // ????????????????????????
        Sheet sheet = workbook.getSheetAt(0);
        List<CCommonPushUsers> emailUsers = cCommonPushUsersService.getEmailAccounts();
        if(!emailUsers.isEmpty()){
            for (int i = 0; i < emailUsers.size(); i++) {
                CCommonPushUsers c = emailUsers.get(i);
                Row row = sheet.createRow(i+2);
                row.createCell(0).setCellValue(c.getName());
                row.createCell(1).setCellValue(c.getAccount());
//                CCommonPushAccountRetativeId cre = cCommonPushAccountRetativeIdService.getIdByEmailAccount(c.getId());
//                row.createCell(2).setCellValue(cre.getId());
            }
        }
        Sheet sheet1 = workbook.getSheetAt(1);
        List<CCommonPushUsers> messageUsers = cCommonPushUsersService.getMessageAccounts();
        if(!messageUsers.isEmpty()){
            for (int i = 0; i < messageUsers.size(); i++) {
                CCommonPushUsers c = messageUsers.get(i);
                Row row = sheet1.createRow(i+4);
                row.createCell(1).setCellValue(c.getAccount());
                row.createCell(2).setCellValue(c.getName());
            }
        }

        response.reset();
        response.addHeader("Content-Disposition", "attachment;filename=\"" + URLEncoder.encode(downLoadName , "UTF-8")+ "\"");
        OutputStream os = new BufferedOutputStream(response.getOutputStream());
        response.setContentType("application/vnd.ms-excel;charset=utf-8");
//            response.setCharacterEncoding("UTF-8");
        //???excel?????????????????????
        workbook.write(os);
        os.flush();
        os.close();
        long endTime=System.currentTimeMillis();
        System.out.println("----------end file "+ fileName+" download.?????????"+(endTime-startTime)+"ms----------");
    }


    @Override
    public List<CCommonPush> getCommPushList() {
        return cCommonPushMapper.selectPushStatusAndPushTypeAndPushThemeAndPushAccountNameByPushType(Constants.SERVICE_TYPE.wx.getIndex());
    }

    @Override
    public String getPushStatisticsForReport() {
        String params ="";
        params+= DateUtils.formatDate(DateUtil.yesterday(),DateUtils.PATTERN_24TIME)+",";
        List<Map<String, Object>> emailResult = cCommonPushMapper.getPushStatusCountByType(DateUtil.yesterday(),Constants.SERVICE_TYPE.yj.getIndex());
        for (Map<String, Object> map:emailResult) {
            map.put(String.valueOf(map.get("name")),String.valueOf(map.get("value")));
        }
        int emailSuccess = 0;
        int emailFail = 0;
        for (Map<String, Object> map:emailResult) {
           if(map.get(Constants.PUSH_STATUS.yjfs.getIndex())!= null){
               emailSuccess = Integer.parseInt(String.valueOf(map.get(Constants.PUSH_STATUS.yjfs.getIndex())));
           }
            if(map.get(Constants.PUSH_STATUS.fsyc.getIndex())!= null){
                emailFail = Integer.parseInt(String.valueOf(map.get(Constants.PUSH_STATUS.fsyc.getIndex())));
            }
        }
        params+=emailSuccess+",";
        params+=emailFail+",";

        List<Map<String, Object>> messageResult = cCommonPushMapper.getPushStatusCountByType(DateUtil.yesterday(),Constants.SERVICE_TYPE.dx.getIndex());
        for (Map<String, Object> map:messageResult) {
            map.put(String.valueOf(map.get("name")),String.valueOf(map.get("value")));
        }
        int messageSuccess = 0;
        int messageFail = 0;
        for (Map<String, Object> map:messageResult) {
            if(map.get(Constants.PUSH_STATUS.yjfs.getIndex())!= null){
                messageSuccess = Integer.parseInt(String.valueOf(map.get(Constants.PUSH_STATUS.yjfs.getIndex())));
            }
            if(map.get(Constants.PUSH_STATUS.fsyc.getIndex())!= null){
                messageFail = Integer.parseInt(String.valueOf(map.get(Constants.PUSH_STATUS.fsyc.getIndex())));
            }
        }
        params+=messageSuccess+",";
        params+=messageFail;
        return params;
    }

    @Override
    public List<CCommonPush> getAllToSendEamilTask() {
        return cCommonPushMapper.selectAllByPushTypeAndPushStatusOrderByIdDesc(Constants.SERVICE_TYPE.yj.getIndex(), Constants.PUSH_STATUS.ddfs.getIndex());
    }


    @Override
    public List<CCommonPush> getAllToSendWxPushTask() {
        return cCommonPushMapper.selectAllByPushTypeAndPushStatus(Constants.SERVICE_TYPE.wx.getIndex(), Constants.PUSH_STATUS.ddfs.getIndex());
    }

    @Override
    public List<CCommonPush> getAllToSendMessageTask() {
        return cCommonPushMapper.selectAllByPushTypeAndPushStatus(Constants.SERVICE_TYPE.dx.getIndex(), Constants.PUSH_STATUS.ddfs.getIndex());
    }
}





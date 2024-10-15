package com.dtmcli.java.sample.controller.pressure_measurement;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dtmcli.java.sample.param.TransNotifyReq;
import com.dtmcli.java.sample.param.TransReq;

import okhttp3.Response;
import pub.dtm.client.constant.Constants;
import pub.dtm.client.exception.FailureException;
import pub.dtm.client.model.responses.DtmResponse;
import pub.dtm.client.utils.HttpUtils;
import pub.dtm.client.utils.JsonUtils;

@RestController
@RequestMapping(("pmeasure/byhttp/"))
public class TccPMeasureByHttpController2 {

	private static AtomicInteger userIdIndex = new AtomicInteger(1);
	private static final String svc = "http://localhost:8081/pmeasure/byhttp";

	@Autowired
	private DataSource dataSource;

	/**
	 * @return
	 * @throws IOException 
	 */
	@RequestMapping("tccBarrier")
	public String tccBarrier() throws IOException {
		int amount = 30;
		int transOutUserId = userIdIndex.getAndAdd(2);
		transOutUserId = transOutUserId >= 1000 ? transOutUserId % 1000: transOutUserId;
		int transInUserId = transOutUserId + 1;
		LocalDateTime transTime = LocalDateTime.now();
		// 用户1 转出30元
		String transOutJson = JsonUtils.toJson(new TransReq(transOutUserId, -amount));
		// 用户2 转入30元
		String transInJson = JsonUtils.toJson(new TransReq(transInUserId, amount));
		// 转账成功通知
		String transNotifyJson = JsonUtils.toJson(new TransNotifyReq(UUID.randomUUID().toString(), transOutUserId, transInUserId, amount, transTime.toString()));
		
		try (Response post = HttpUtils.post(svc + "/barrierTransOut", transOutJson)) {};
		try (Response post = HttpUtils.post(svc + "/barrierTransIn", transInJson)) {};
//		try (Response post = HttpUtils.post(svc + "/barrierTransNotify", transNotifyJson)) {};
		return "success";
	}

    // ---------------- 转出阶段 ----------------
	@RequestMapping("barrierTransOut")
    public Object TransOutTry(HttpServletRequest request) throws Exception {
        TransReq transReq = extracted(request);
        try (Connection connection = dataSource.getConnection()) {
		    System.out.println("用户: +" + transReq.getUserId() + ",转出" + Math.abs(transReq.getAmount()) + "元准备");
		    this.adjustTrading(connection, transReq);
		    this.adjustBalance(connection, transReq);
        }
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
    
    // ---------------- 转入阶段 ---------------- 
    @RequestMapping("barrierTransIn")
    public Object TransInTry(HttpServletRequest request) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
        	TransReq transReq = extracted(request);
    		System.out.println("用户: +" + transReq.getUserId() + ",转入" + Math.abs(transReq.getAmount()) + "元准备");
    		this.adjustTrading(connection, transReq);
    		this.adjustBalance(connection, transReq);
        }
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
    // ---------------- 转账短信通知 ---------------- 
    @RequestMapping("barrierTransNotifyTry")
    public Object tryTransNotify(HttpServletRequest request) throws Exception {
    	try (Connection connection = dataSource.getConnection()) {
    		TransNotifyReq transReq = extracted(request, TransNotifyReq.class);
			this.submitNotifyRecord(connection, transReq);
			this.adjustNotifyRecord(connection, transReq, "SEND_CONFIRM");
    	}
    	return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
	private void submitNotifyRecord(Connection connection, TransNotifyReq transReq) throws SQLException, FailureException {
        String sql = """
        		INSERT INTO `dtm_busi`.`user_trans_notify` (`trans_id`, `trans_out_user_id`, `trans_in_user_id`, `trans_amount`, `notify_status`, `trans_time`) 
        			VALUES (?, ?, ?, ?, ?, ?);
        		""";
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, transReq.transId());
            preparedStatement.setInt(2, transReq.transOutUserId());
            preparedStatement.setInt(3, transReq.transInUserId());
            preparedStatement.setInt(4, transReq.amount());
            preparedStatement.setString(5, "SEND_PENDING");
            preparedStatement.setString(6, transReq.transTime().toString());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("转账通知记录创建完成");
            } else {
                throw new FailureException("创建失败");
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }
    }
    
    private void adjustNotifyRecord(Connection connection, TransNotifyReq transReq, String status) throws SQLException {
        PreparedStatement preparedStatement = null;
        try {
            String sql = "update dtm_busi.user_trans_notify set notify_status = ? where trans_id = ?";
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, status);
            preparedStatement.setString(2, transReq.transId());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("转账通知状态更新完成");
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }
    }
    
    /**
     * 提取body中参数
     *
     * @param request
     * @return
     * @throws IOException
     */
    private TransReq extracted(HttpServletRequest request) throws IOException {
        byte[] bytes = StreamUtils.copyToByteArray(request.getInputStream());
        return JsonUtils.parseJson(bytes, TransReq.class);
    }
    
    /**
     * 提取body中参数
     *
     * @param request
     * @return
     * @throws IOException
     */
    private <T> T extracted(HttpServletRequest request, Class<T> type) throws IOException {
    	byte[] bytes = StreamUtils.copyToByteArray(request.getInputStream());
    	return JsonUtils.parseJson(bytes, type);
    }
    
    /**
     * 更新交易金额
     *
     * @param connection
     * @param transReq
     * @throws SQLException
     */
    public void adjustTrading(Connection connection, TransReq transReq) throws Exception {
        String sql = "update dtm_busi.user_account set trading_balance=trading_balance+?"
                + " where user_id=? and trading_balance + ? + balance >= 0";
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, transReq.getAmount());
            preparedStatement.setInt(2, transReq.getUserId());
            preparedStatement.setInt(3, transReq.getAmount());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("交易金额更新成功");
            } else {
                throw new FailureException("交易失败:" + JsonUtils.toJson(transReq));
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }
        
    }
    
    /**
     * 更新余额
     */
    public void adjustBalance(Connection connection, TransReq transReq) throws SQLException {
        PreparedStatement preparedStatement = null;
        try {
            String sql = "update dtm_busi.user_account set trading_balance=trading_balance-?,balance=balance+? where user_id=?";
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, transReq.getAmount());
            preparedStatement.setInt(2, transReq.getAmount());
            preparedStatement.setInt(3, transReq.getUserId());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("余额更新成功");
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }
    }

}
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dtmcli.java.sample.param.TransNotifyReq;
import com.dtmcli.java.sample.param.TransReq;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import pub.dtm.client.DtmClient;
import pub.dtm.client.barrier.BranchBarrier;
import pub.dtm.client.constant.Constants;
import pub.dtm.client.exception.FailureException;
import pub.dtm.client.model.responses.DtmResponse;
import pub.dtm.client.utils.JsonUtils;

@Slf4j
@RestController
@RequestMapping(("pmeasure/byDtm/"))
public class TccPMeasureByDtmController {

	private static AtomicInteger userIdIndex = new AtomicInteger(1);
	private static final String svc = "http://192.168.10.42:8081/pmeasure/byDtm";
//	private static final String svc = "http://127.0.0.1:8081/pmeasure/byDtm";

	@Value("${dtm.ipport}")
	private String endpoint;
	@Autowired
	private DataSource dataSource;

	/**
	 * 具有子事务屏障功能的tcc demo (转账成功)
	 *
	 * @return
	 */
	@RequestMapping("tccBarrier")
	public String tccBarrier(@RequestParam(required = false, defaultValue = "true") boolean notify) {
		// 创建dmt client
		DtmClient dtmClient = new DtmClient(endpoint);
		// 创建tcc事务
		String customGid = UUID.randomUUID().toString();
		try {
			dtmClient.tccGlobalTransaction(
					customGid, 
					tcc -> {
						int amount = 30;
						int transOutUserId = userIdIndex.getAndAdd(2);
						transOutUserId = transOutUserId >= 1000 ? transOutUserId % 1000: transOutUserId;
						LocalDateTime transTime = LocalDateTime.now();
						// 用户1 转出30元
						try (Response outResponse = tcc.callBranch(
								new TransReq(transOutUserId, -amount),
								svc + "/barrierTransOutTry",
								svc + "/barrierTransOutConfirm",
								svc + "/barrierTransOutCancel")) {
							log.info("outResponse:{}", outResponse);
						}
						// 用户2 转入30元
						int transInUserId = transOutUserId + 1;
						try (Response inResponse = tcc.callBranch(
								new TransReq(transInUserId, amount), 
								svc + "/barrierTransInTry",
								svc + "/barrierTransInConfirm",
								svc + "/barrierTransInCancel")) {
							log.info("inResponse:{}", inResponse);
						}
						if (notify) {
							// 用户1/2 提交短信通知任务
							try (Response callBranch = tcc.callBranch(
									new TransNotifyReq(tcc.getGid(), transOutUserId, transInUserId, amount, transTime.toString()), 
									svc + "/barrierTransNotifyTry",
									svc + "/barrierTransNotifyConfirm",
									svc + "/barrierTransCancelCancel")) {
							};
						}
					});
		} catch (Exception e) {
			log.error("tccGlobalTransaction error", e);
			return "fail";
		}
		return "success";
	}

    // ---------------- 转出阶段 ----------------
	@RequestMapping("barrierTransOutTry")
    public Object TransOutTry(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        log.info("barrierTransOutTry branchBarrier:{}", branchBarrier);
        TransReq transReq = extracted(request);
        try (Connection connection = dataSource.getConnection()) {
			branchBarrier.call(connection, (barrier) -> {
			    System.out.println("用户: +" + transReq.getUserId() + ",转出" + Math.abs(transReq.getAmount()) + "元准备");
			    this.adjustTrading(connection, transReq);
			});
		}
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
    
    @RequestMapping("barrierTransOutConfirm")
    public Object TransOutConfirm(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        log.info("barrierTransOutConfirm branchBarrier:{}", branchBarrier);
        try (Connection connection = dataSource.getConnection()) {
			TransReq transReq = extracted(request);
			branchBarrier.call(connection, (barrier) -> {
			    System.out.println("用户: +" + transReq.getUserId() + ",转出" + Math.abs(transReq.getAmount()) + "元提交");
			    adjustBalance(connection, transReq);
			});
		}
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
    @RequestMapping("barrierTransOutCancel")
    public Object TransOutCancel(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        log.info("barrierTransOutCancel branchBarrier:{}", branchBarrier);
        TransReq transReq = extracted(request);
        try (Connection connection = dataSource.getConnection()) {
        	branchBarrier.call(connection, (barrier) -> {
        		System.out.println("用户: +" + transReq.getUserId() + ",转出" + Math.abs(transReq.getAmount()) + "元回滚");
        		this.adjustTrading(connection, transReq);
        	});
        }
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
    // ---------------- 转入阶段 ---------------- 
    @RequestMapping("barrierTransInTry")
    public Object TransInTry(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        log.info("barrierTransInTry branchBarrier:{}", branchBarrier);
        try (Connection connection = dataSource.getConnection()) {
        	TransReq transReq = extracted(request);
        	branchBarrier.call(connection, (barrier) -> {
        		System.out.println("用户: +" + transReq.getUserId() + ",转入" + Math.abs(transReq.getAmount()) + "元准备");
        		this.adjustTrading(connection, transReq);
        	});
        }
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
    @RequestMapping("barrierTransInConfirm")
    public Object TransInConfirm(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        log.info("barrierTransInConfirm TransInCancel branchBarrier:{}", branchBarrier);
        try (Connection connection = dataSource.getConnection()) {
        	TransReq transReq = extracted(request);
        	branchBarrier.call(connection, (barrier) -> {
        		System.out.println("用户: +" + transReq.getUserId() + ",转入" + Math.abs(transReq.getAmount()) + "元提交");
        		adjustBalance(connection, transReq);
        	});
        }
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
    @RequestMapping("barrierTransInCancel")
    public Object TransInCancel(HttpServletRequest request) throws Exception {
        BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
        log.info("barrierTransInCancel branchBarrier:{}", branchBarrier);
        try (Connection connection = dataSource.getConnection()) {
        	TransReq transReq = extracted(request);
        	branchBarrier.call(connection, (barrier) -> {
        		System.out.println("用户: +" + transReq.getUserId() + ",转入" + Math.abs(transReq.getAmount()) + "回滚");
        		this.adjustTrading(connection, transReq);
        	});
        }
        return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
    // ---------------- 转账短信通知 ---------------- 
    @RequestMapping("barrierTransNotifyTry")
    public Object tryTransNotify(HttpServletRequest request) throws Exception {
    	BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
    	log.info("barrierTransNotifyTry branchBarrier:{}", branchBarrier);
    	try (Connection connection = dataSource.getConnection()) {
    		TransNotifyReq transReq = extracted(request, TransNotifyReq.class);
    		branchBarrier.call(connection, (barrier) -> {
    			this.submitNotifyRecord(connection, transReq);
    		});
    	}
    	return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
    
	@RequestMapping("barrierTransNotifyConfirm")
    public Object confirmTransNotify(HttpServletRequest request) throws Exception {
    	BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
    	log.info("barrierTransNotifyConfirm TransInCancel branchBarrier:{}", branchBarrier);
    	try (Connection connection = dataSource.getConnection()) {
    		TransNotifyReq transReq = extracted(request, TransNotifyReq.class);
    		branchBarrier.call(connection, (barrier) -> {
    			adjustNotifyRecord(connection, transReq, "SEND_CONFIRM");
    		});
    	}
    	return DtmResponse.buildDtmResponse(Constants.SUCCESS_RESULT);
    }
	
	@RequestMapping("barrierTransCancelCancel")
    public Object cancelTransNotify(HttpServletRequest request) throws Exception {
    	BranchBarrier branchBarrier = new BranchBarrier(request.getParameterMap());
    	log.info("barrierTransCancelCancel branchBarrier:{}", branchBarrier);
    	try (Connection connection = dataSource.getConnection()) {
    		TransNotifyReq transReq = extracted(request, TransNotifyReq.class);
    		branchBarrier.call(connection, (barrier) -> {
    			adjustNotifyRecord(connection, transReq, "SEND_CANCEL");
    		});
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
package com.mao.core.func.server;

import com.mao.common.HexUtils;
import com.mao.common.MLogger;
import com.mao.core.conn.client.DataFuture;
import com.mao.core.conn.server.P698Server;
import com.mao.core.exception.P698TimeOutException;
import com.mao.core.p698.AttrEnum;
import com.mao.core.p698.P698Attr;
import com.mao.core.p698.P698Resp;
import com.mao.core.p698.P698Utils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 698服务端服务类
 * @author mao
 * @date 2023/8/28 14:53
 */
public class P698ServerService {
    private final P698Server server;
    private long timeout = 5_000;

    private Supplier<Integer> invokeSupplier;

    public P698ServerService setInvokeSupplier(Supplier<Integer> invokeSupplier) {
        this.invokeSupplier = invokeSupplier;
        return this;
    }

    public P698ServerService(P698Server server) {
        this.server = server;
        this.invokeSupplier = server.invokeSupplier();
    }

    public P698ServerService(P698Server server, long timeout) {
        this.server = server;
        this.timeout = timeout;
    }

    /**
     * 读取电表指定的属性值
     *
     * @param meterAddress 电表地址 eg: 39 12 19 08 37 00
     * @param attrs        电表属性列表
     * @return 电表响应对象
     */
    public P698Resp get(String meterAddress, AttrEnum... attrs) throws InterruptedException {
        P698Utils.P698MsgBuilder builder = P698Utils.getBuilder(this.invokeSupplier);
        for (AttrEnum attr : attrs) {
            builder.addAttr(attr);
        }
        builder.setMeterAddress(meterAddress);
        P698Utils.P698Msg p698Msg = builder.build();
        MLogger.log("构建的数据包:" + HexUtils.bytes2HexString(p698Msg.getRawData()));
        List<DataFuture<P698Resp>> request = server.request(p698Msg);
        // TODO 可能有多个响应, 暂时先返回第一个
        if(request.size() > 0) {
            return request.get(0).get(timeout, TimeUnit.MILLISECONDS);
        }
        return null; // 没有客户端连接
    }

    public <T> T get(String meterAddress, Function<P698Attr, T> func, AttrEnum attrEnum) throws InterruptedException {
        P698Resp resp = get(meterAddress, attrEnum);
        if(resp == null) { // 超时或者没有客户端连接
            throw new P698TimeOutException("读取属性超时 - " + attrEnum.getName());
        }
        for (P698Attr attr : resp.getAttrs()) {
            if (AttrEnum.getAttrEnum(attr.getOI()) == attrEnum) {
                if (attr.isError()) {
                    throw new RuntimeException(attr.getError());
                }
                return func.apply(attr);
            }
        }
        throw new RuntimeException("属性未找到");
    }

    // 读取反向有功电能
    public List<Double> getRapR(String meterAddress) throws InterruptedException {
        return this.get(meterAddress, (attr) -> (List<Double>) attr.getData(), AttrEnum.P0020);
    }

    // 读取正向有功电能
    public List<Double> getPapR(String meterAddress) throws InterruptedException {
        return this.get(meterAddress, (attr) -> (List<Double>) attr.getData(), AttrEnum.P0010);
    }
}
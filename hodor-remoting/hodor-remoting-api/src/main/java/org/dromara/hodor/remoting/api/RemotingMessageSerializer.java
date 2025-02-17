package org.dromara.hodor.remoting.api;

import java.lang.reflect.Type;
import org.dromara.hodor.common.extension.SPI;

/**
 *  remoting message serializer
 *
 * @author tomgs
 * @version 2021/2/24 1.0 
 */
@SPI("serializer")
public interface RemotingMessageSerializer {

    /**
     * 序列化操作
     */
    byte[] serialize(Object requestBody);

    /**
     * 反序列化根据Class类型
     */
    <T> T deserialize(byte[] byteData, Class<T> cls);

    /**
     * 反序列化根据Type类型，这种可以支持泛型
     */
    <T> T deserialize(byte[] byteData, Type typeOfT);

}

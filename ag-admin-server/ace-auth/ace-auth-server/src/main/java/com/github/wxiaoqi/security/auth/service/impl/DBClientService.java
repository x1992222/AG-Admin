package com.github.wxiaoqi.security.auth.service.impl;

import com.github.wxiaoqi.security.auth.bean.ClientInfo;
import com.github.wxiaoqi.security.auth.common.event.AuthRemoteEvent;
import com.github.wxiaoqi.security.auth.entity.Client;
import com.github.wxiaoqi.security.auth.mapper.ClientMapper;
import com.github.wxiaoqi.security.auth.service.ClientService;
import com.github.wxiaoqi.security.auth.util.client.ClientTokenUtil;
import com.github.wxiaoqi.security.common.exception.auth.ClientInvalidException;
import com.github.wxiaoqi.security.common.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ace on 2017/9/10.
 */
@Service
public class DBClientService implements ClientService {
    @Autowired
    private ClientMapper clientMapper;
    @Autowired
    private ClientTokenUtil clientTokenUtil;
    @Autowired
    private DiscoveryClient discovery;
    private ApplicationContext context;

    @Autowired
    public DBClientService(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public String apply(String clientId, String secret) throws Exception {
        Client client = getClient(clientId, secret);
        return clientTokenUtil.generateToken(new ClientInfo(client.getCode(),client.getName(),client.getId().toString()));
    }

    private Client getClient(String clientId, String secret) {
        Client client = new Client();
        client.setCode(clientId);
        client = clientMapper.selectOne(client);
        if(client==null||!client.getSecret().equals(secret)){
            throw new ClientInvalidException("Client not found or Client secret is error!");
        }
        return client;
    }

    public void validate(String clientId, String secret) throws Exception {
        Client client = new Client();
        client.setCode(clientId);
        client = clientMapper.selectOne(client);
        if(client==null||!client.getSecret().equals(secret)){
            throw new ClientInvalidException("Client not found or Client secret is error!");
        }
    }

    @Override
    public List<String> getAllowedClient(String clientId, String secret) {
        Client info = this.getClient(clientId, secret);
        List<String> clients = clientMapper.selectAllowedClient(info.getId() + "");
        if(clients==null) {
            new ArrayList<String>();
        }
        return clients;
    }

    private Client getClient(String clientId) {
        Client client = new Client();
        client.setCode(clientId);
        client = clientMapper.selectOne(client);
        return client;
    }

    @Override
    @Scheduled(cron = "0 0/1 * * * ?")
    public void registryClient() {
        // 自动注册节点
        discovery.getServices().forEach((name) ->{
            Client client = new Client();
            client.setName(name);
            client.setCode(name);
            Client dbClient = clientMapper.selectOne(client);
            if(dbClient==null) {
                client.setSecret(UUIDUtils.generateShortUuid());
                clientMapper.insert(client);
            }else{
                // 主动推送
                final List<String> clients = clientMapper.selectAllowedClient(dbClient.getId() + "");
                final String myUniqueId = context.getId();
                final AuthRemoteEvent event =
                        new AuthRemoteEvent(this, myUniqueId, name, clients);
                context.publishEvent(event);
            }
        });
    }
}

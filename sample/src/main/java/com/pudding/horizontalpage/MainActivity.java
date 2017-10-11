package com.pudding.horizontalpage;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;

import com.pudding.pagelayoutmanager.PageLayoutManager;

public class MainActivity extends AppCompatActivity{
    RecyclerView recyclerView;
    MyAdapter myAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myAdapter = new MyAdapter();
        recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        recyclerView.setAdapter(myAdapter);
        PageLayoutManager manager = new PageLayoutManager(2, 5);
        recyclerView.setLayoutManager(manager);

        //添加上下及左右的边距
        manager.setMarginHorizontal(80);
        manager.setMarginVertical(60);

        //需要有翻页效果续绑定RecyclerView
        manager.bindRecyclerView(recyclerView);

        //拖拽可以自己实现
        // 也可以让adapter实现OnCallBackNeedDate接口后调用下面两个方法实现
        manager.setOnCallBackNeedDate(myAdapter);
        manager.enableDragItem(true);
    }
}

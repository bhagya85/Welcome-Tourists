package com.welcometourist;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.Toast;
 

public class MainActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        addKeyListener();
    }

    public void addKeyListener() {
    	 
    	// get edittext component
    final EditText edittext = (EditText) findViewById(R.id.editText);
     
    	// add a keylistener to keep track user input
    	edittext.setOnKeyListener(new OnKeyListener() {
    	public boolean onKey(View v, int keyCode, KeyEvent event) {
     
    		// if keydown and "enter" is pressed
    		if ((event.getAction() == KeyEvent.ACTION_DOWN)
    			&& (keyCode == KeyEvent.KEYCODE_ENTER)) {
     
    			// display a floating message
    			Toast.makeText(MainActivity.this,
    		//	final EditText = (EditText) findViewById(R.id.editText)
    			edittext.getText(), Toast.LENGTH_LONG).show();
    			return true;
     
    		} else if ((event.getAction() == KeyEvent.ACTION_DOWN)
    			&& (keyCode == KeyEvent.KEYCODE_9)) {
     
    			// display a floating message
    			Toast.makeText(MainActivity.this,
    				"Number 9 is pressed!", Toast.LENGTH_LONG).show();
    			return true;
    		}
     
    		return false;
    	}
     });

    
}
}

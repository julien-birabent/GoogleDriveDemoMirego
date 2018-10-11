package com.me.julienbirabent.googledrivedemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.drive.*
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.Query
import com.google.android.gms.drive.query.SearchableField
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.io.OutputStreamWriter


class MainActivity : AppCompatActivity() {


    companion object {
        private const val GOOGLE_SIGNIN_BACKUP_REQUEST_CODE = 1
        private const val REQUEST_CODE_CREATE_FILE = 2
    }

    private lateinit var googleSignInClient: GoogleSignInClient
    private var account: GoogleSignInAccount? = null
    private var driveClient: DriveClient? = null
    private var driveResourceClient: DriveResourceClient? = null

    private lateinit var signIn: Button
    private lateinit var signOut: Button
    private lateinit var createFile: Button
    private lateinit var openFile: Button
    private lateinit var fileResultTextView: TextView
    private lateinit var fileInput: EditText
    private lateinit var fileOutput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        googleSignInClient = buildGoogleSignInClient()

        signIn = findViewById(R.id.signIn)
        signOut = findViewById(R.id.signOut)
        createFile = findViewById(R.id.createFile)
        openFile = findViewById(R.id.openFile)
        fileResultTextView = findViewById(R.id.fileResult)
        fileInput = findViewById(R.id.fileInput)
        fileOutput = findViewById(R.id.fileOutput)

        signIn.setOnClickListener {
            signIn()
        }
        signOut.setOnClickListener {
            signOut()
        }

        createFile.setOnClickListener { createFileInAppFolder(fileInput.text.toString()) }
        openFile.setOnClickListener { openFileFor(fileOutput.text.toString()) }

    }

    override fun onStart() {
        super.onStart()
        googleSignInClient = buildGoogleSignInClient()
        account = GoogleSignIn.getLastSignedInAccount(this)
        initClient()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GOOGLE_SIGNIN_BACKUP_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                handleSignInResult(task)
            }
        }
        if (requestCode == REQUEST_CODE_CREATE_FILE) {

        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            account = completedTask.getResult(ApiException::class.java)
            initClient()
            account?.let { Log.d("Google drive", "successfuly disconnected") }

            // Signed in successfully, show authenticated UI.
        } catch (e: ApiException) {
        }

    }

    private fun initClient() {
        driveClient = account?.let { Drive.getDriveClient(this, it) }
        driveResourceClient = account?.let { Drive.getDriveResourceClient(this, it) }
    }

    private fun buildGoogleSignInClient(): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Drive.SCOPE_FILE)
            .requestScopes(Drive.SCOPE_APPFOLDER)
            .build()
        return GoogleSignIn.getClient(this, signInOptions)
    }

    private fun signIn() {
        startActivityForResult(googleSignInClient.signInIntent, GOOGLE_SIGNIN_BACKUP_REQUEST_CODE)
    }

    private fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            Log.d("Google Drive Demo", "successfuly disconnected")
        }

    }

    fun openFileFor(fileName: String) {
        val query = Query.Builder()
            .addFilter(Filters.eq(SearchableField.TITLE, fileName))
            .build()

        val queryTask = driveResourceClient?.query(query)

        queryTask
            ?.addOnSuccessListener(this
            ) {

                for (data in it) {
                    fileResultTextView.append("\n " + "file title : " + data.originalFilename)
                    openFile(data.driveId.asDriveFile())
                }
                it.release()
            }
            ?.addOnFailureListener(this) {
                // Handle failure...
                // ...
            }
    }

    private fun openFile(file: DriveFile) {

        val openTask = driveResourceClient?.openFile(file, DriveFile.MODE_READ_ONLY)

        openTask?.continueWithTask { task ->
            val contents = task.result
            // access content of file here
            val input = contents?.inputStream
            val inputAsString = input?.bufferedReader().use { it?.readText() }
            fileResultTextView.append("\n " + "file content : " + inputAsString.toString())
            val discardTask = driveResourceClient?.discardContents(contents!!)
            discardTask
        }
            ?.addOnFailureListener {
                // Handle failure
                // ...
            }
    }

    private fun createFileInAppFolder(title: String) {
        val appFolderTask = driveResourceClient?.appFolder
        val createContentsTask = driveResourceClient?.createContents()

        Tasks.whenAll(appFolderTask, createContentsTask)
            .continueWithTask<Any> {
                val parent = appFolderTask?.result
                val contents = createContentsTask?.result
                val outputStream = contents!!.outputStream
                OutputStreamWriter(outputStream).use { writer -> writer.write("dummy content for now") }

                val changeSet = MetadataChangeSet.Builder()
                    .setTitle(title)
                    .setMimeType("text/plain")
                    .setStarred(true)
                    .build()

                driveResourceClient?.createFile(parent!!, changeSet, contents) as Task<Any>
            }
            .addOnSuccessListener(this) {
                Log.d("Google Drive Demo", "file written in app folder")
            }

            .addOnFailureListener(this) {
                Log.d("Google Drive Demo", "fail to write the file in app folder")
            }
    }
}

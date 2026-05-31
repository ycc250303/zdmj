const local: App.I18n.Schema = {
  system: {
    title: 'CareerGuidance',
    updateTitle: 'System Version Update Notification',
    updateContent: 'A new version of the system has been detected. Do you want to refresh the page immediately?',
    updateConfirm: 'Refresh immediately',
    updateCancel: 'Later'
  },
  common: {
    action: 'Action',
    add: 'Add',
    addSuccess: 'Add Success',
    backToHome: 'Back to home',
    batchDelete: 'Batch Delete',
    cancel: 'Cancel',
    close: 'Close',
    check: 'Check',
    expandColumn: 'Expand Column',
    columnSetting: 'Column Setting',
    config: 'Config',
    confirm: 'Confirm',
    delete: 'Delete',
    deleteSuccess: 'Delete Success',
    confirmDelete: 'Are you sure you want to delete?',
    edit: 'Edit',
    warning: 'Warning',
    error: 'Error',
    index: 'Index',
    keywordSearch: 'Please enter keyword',
    logout: 'Logout',
    logoutConfirm: 'Are you sure you want to log out?',
    lookForward: 'Coming soon',
    modify: 'Modify',
    modifySuccess: 'Modify Success',
    noData: 'No Data',
    operate: 'Operate',
    pleaseCheckValue: 'Please check whether the value is valid',
    refresh: 'Refresh',
    requestFailed: 'Request failed',
    reset: 'Reset',
    search: 'Search',
    switch: 'Switch',
    tip: 'Tip',
    trigger: 'Trigger',
    update: 'Update',
    updateSuccess: 'Update Success',
    userCenter: 'User Center',
    yesOrNo: {
      yes: 'Yes',
      no: 'No'
    }
  },
  request: {
    logout: 'Logout user after request failed',
    logoutMsg: 'User status is invalid, please log in again',
    logoutWithModal: 'Pop up modal after request failed and then log out user',
    logoutWithModalMsg: 'User status is invalid, please log in again',
    refreshToken: 'The requested token has expired, refresh the token',
    tokenExpired: 'The requested token has expired'
  },
  theme: {
    themeDrawerTitle: 'Theme Configuration',
    tabs: {
      appearance: 'Appearance',
      layout: 'Layout',
      general: 'General',
      preset: 'Preset'
    },
    appearance: {
      themeSchema: {
        title: 'Theme Schema',
        light: 'Light',
        dark: 'Dark',
        auto: 'Follow System'
      },
      grayscale: 'Grayscale',
      colourWeakness: 'Colour Weakness',
      themeColor: {
        title: 'Theme Color',
        primary: 'Primary',
        info: 'Info',
        success: 'Success',
        warning: 'Warning',
        error: 'Error',
        followPrimary: 'Follow Primary'
      },
      themeRadius: {
        title: 'Theme Radius'
      },
      recommendColor: 'Apply Recommended Color Algorithm',
      recommendColorDesc: 'The recommended color algorithm refers to',
      preset: {
        title: 'Theme Presets',
        apply: 'Apply',
        applySuccess: 'Preset applied successfully',
        default: {
          name: 'Default Preset',
          desc: 'Default theme preset with balanced settings'
        },
        dark: {
          name: 'Dark Preset',
          desc: 'Dark theme preset for night time usage'
        },
        compact: {
          name: 'Compact Preset',
          desc: 'Compact layout preset for small screens'
        },
        azir: {
          name: "Azir's Preset",
          desc: 'It is a cold and elegant preset that Azir likes'
        }
      }
    },
    layout: {
      layoutMode: {
        title: 'Layout Mode',
        vertical: 'Vertical Mode',
        horizontal: 'Horizontal Mode',
        'vertical-mix': 'Vertical Mix Mode',
        'vertical-hybrid-header-first': 'Left Hybrid Header-First',
        'top-hybrid-sidebar-first': 'Top-Hybrid Sidebar-First',
        'top-hybrid-header-first': 'Top-Hybrid Header-First',
        vertical_detail: 'Vertical menu layout, with the menu on the left and content on the right.',
        'vertical-mix_detail':
          'Vertical mix-menu layout, with the primary menu on the dark left side and the secondary menu on the lighter left side.',
        'vertical-hybrid-header-first_detail':
          'Left hybrid layout, with the primary menu at the top, the secondary menu on the dark left side, and the tertiary menu on the lighter left side.',
        horizontal_detail: 'Horizontal menu layout, with the menu at the top and content below.',
        'top-hybrid-sidebar-first_detail':
          'Top hybrid layout, with the primary menu on the left and the secondary menu at the top.',
        'top-hybrid-header-first_detail':
          'Top hybrid layout, with the primary menu at the top and the secondary menu on the left.'
      },
      tab: {
        title: 'Tab Settings',
        visible: 'Tab Visible',
        cache: 'Tag Bar Info Cache',
        cacheTip: 'One-click to open/close global keepalive',
        height: 'Tab Height',
        mode: {
          title: 'Tab Mode',
          slider: 'Slider',
          chrome: 'Chrome',
          button: 'Button'
        },
        closeByMiddleClick: 'Close Tab by Middle Click',
        closeByMiddleClickTip: 'Enable closing tabs by clicking with the middle mouse button'
      },
      header: {
        title: 'Header Settings',
        height: 'Header Height',
        breadcrumb: {
          visible: 'Breadcrumb Visible',
          showIcon: 'Breadcrumb Icon Visible'
        }
      },
      sider: {
        title: 'Sider Settings',
        inverted: 'Dark Sider',
        width: 'Sider Width',
        collapsedWidth: 'Sider Collapsed Width',
        mixWidth: 'Mix Sider Width',
        mixCollapsedWidth: 'Mix Sider Collapse Width',
        mixChildMenuWidth: 'Mix Child Menu Width'
      },
      footer: {
        title: 'Footer Settings',
        visible: 'Footer Visible',
        fixed: 'Fixed Footer',
        height: 'Footer Height',
        right: 'Right Footer'
      },
      content: {
        title: 'Content Area Settings',
        scrollMode: {
          title: 'Scroll Mode',
          tip: 'The theme scroll only scrolls the main part, the outer scroll can carry the header and footer together',
          wrapper: 'Wrapper',
          content: 'Content'
        },
        page: {
          animate: 'Page Animate',
          mode: {
            title: 'Page Animate Mode',
            fade: 'Fade',
            'fade-slide': 'Slide',
            'fade-bottom': 'Fade Zoom',
            'fade-scale': 'Fade Scale',
            'zoom-fade': 'Zoom Fade',
            'zoom-out': 'Zoom Out',
            none: 'None'
          }
        },
        fixedHeaderAndTab: 'Fixed Header And Tab'
      }
    },
    general: {
      title: 'General Settings',
      watermark: {
        title: 'Watermark Settings',
        visible: 'Watermark Full Screen Visible',
        text: 'Custom Watermark Text',
        enableUserName: 'Enable User Name Watermark',
        enableTime: 'Show Current Time',
        timeFormat: 'Time Format'
      },
      multilingual: {
        title: 'Multilingual Settings',
        visible: 'Display multilingual button'
      },
      globalSearch: {
        title: 'Global Search Settings',
        visible: 'Display GlobalSearch button'
      }
    },
    configOperation: {
      copyConfig: 'Copy Config',
      copySuccessMsg: 'Copy Success, Please replace the variable "themeSettings" in "src/theme/settings.ts"',
      resetConfig: 'Reset Config',
      resetSuccessMsg: 'Reset Success'
    }
  },
  route: {
    login: 'Login',
    403: 'No Permission',
    404: 'Page Not Found',
    500: 'Server Error',
    'iframe-page': 'Iframe',
    home: 'Home',
    portal: 'portal',
    resumes: 'Resume Library',
    'resumes-editor': 'Resume Editor',
    "profile": "Personal Information",
    "profile_project": "Portfolio",
    "profile_education": 'Education',
    "profile_career":'Career',
    "profile_skill":'Skills',
    "profile_basic-info":'Basic Info',
    "profile_capability": 'Capability Profile',
    "knowledge": 'Knowledge Base',
    "chat": 'AI Chat',
    "jobs": 'Jobs',
    "job-detail": 'Job Details',
    "job-edit": 'Edit Job',
    'user-settings': 'User Settings'
  },
  page: {
    login: {
      common: {
        loginOrRegister: 'Login / Register',
        userNamePlaceholder: 'Please enter user name',
        phonePlaceholder: 'Please enter phone number',
        emailPlaceholder: 'Please enter email address',
        codePlaceholder: 'Please enter verification code',
        passwordPlaceholder: 'Please enter password',
        confirmPasswordPlaceholder: 'Please enter password again',
        codeLogin: 'Verification code login',
        confirm: 'Confirm',
        back: 'Back',
        validateSuccess: 'Verification passed',
        loginSuccess: 'Login successfully',
        welcomeBack: 'Welcome back, {userName} !'
      },
      pwdLogin: {
        title: 'Password Login',
        rememberMe: 'Remember me',
        forgetPassword: 'Forget password?',
        register: 'Register',
        otherAccountLogin: 'Other Account Login',
        otherLoginMode: 'Other Login Mode',
        superAdmin: 'Super Admin',
        admin: 'Admin',
        user: 'User'
      },
      codeLogin: {
        title: 'Verification Code Login',
        getCode: 'Get verification code',
        reGetCode: 'Reacquire after {time}s',
        sendCodeSuccess: 'Verification code sent successfully',
        imageCodePlaceholder: 'Please enter image verification code',
        notSupported: 'Verification code login is not supported yet, please use password login.'
      },
      register: {
        title: 'Register',
        agreement: 'I have read and agree to',
        protocol: '《User Agreement》',
        policy: '《Privacy Policy》',
        success: 'Registration successful! Go login!',
        sendCodeSuccess: 'Code sent successfully, please check your email!'
      },
      resetPwd: {
        title: 'Reset Password',
        success: 'Password reset successful, go login!'
      },
      bindWeChat: {
        title: 'Bind WeChat'
      }
    },
    home: {
      hero: {
        badge: '✨ AI Career Assistant',
        title: 'Find Your Dream Job with AI',
        subtitle: 'Smart chat, knowledge base, and resume generation — an all-in-one job hunting solution',
        startChat: 'Start Chat',
        exploreKnowledge: 'Explore Knowledge'
      },
      quickActions: {
        title: 'Quick Start',
        subtitle: 'Choose a feature and begin your intelligent career journey'
      },
      aiChat: {
        title: 'AI Chat',
        description: 'Real-time conversations with AI assistant for career guidance'
      },
      knowledge: {
        title: 'Knowledge Base',
        description: 'Upload study materials and let AI help you retrieve and understand them'
      },
      resume: {
        title: 'Resume Generation',
        description: 'Quickly create professional resumes highlighting your skills'
      },
      jobs: {
        title: 'Job Positions',
        description: 'Browse and manage job positions, generate job capability profiles'
      },
      features: {
        title: 'Core Features',
        subtitle: 'Powerful features to support your job hunting journey',
        smartQA: {
          title: 'Smart Q&A',
          description: 'AI-powered answers for all your career questions'
        },
        knowledgeBase: {
          title: 'Knowledge Management',
          description: 'Upload and manage materials in PDF, Markdown and more'
        },
        resumeGen: {
          title: 'Resume Generation',
          description: 'AI-assisted professional resume creation'
        },
        fastCreate: {
          title: 'Quick Creation',
          description: 'Generate complete resumes in just a few steps'
        }
      },
      tips: {
        title: 'Pro Tips',
        tip1: 'Upload your study materials and project docs to knowledge base first',
        tip2: 'Get personalized career advice through AI chat',
        tip3: 'Use resume generation to quickly create professional resumes'
      }
    },
    portal: {
      title: 'Career Guidance',
      hello: 'Hello, ',
      enterConsole: 'Enter Console',
      loginSystem: 'Login System',
      heroTitle: 'Welcome to SmartHire\nIntelligent Career Navigation',
      heroDesc: 'An intelligent, efficient, one-stop job hunting solution for software engineering students',
      actionEnter: 'Enter Console',
      actionLogin: 'Experience Now'
    },
    chat: {
      newChat: 'New Chat',
      startNewConversation: 'Start a new conversation~~',
      aiAssistant: 'AI Assistant',
      inputPlaceholder: 'Send a message to AI assistant',
      collapseChatList: 'Collapse chat list',
      expandChatList: 'Expand chat list',
      deleteConfirm: 'Are you sure you want to delete this conversation?',
      deleteSuccess: 'Delete successful',
      deleteFailed: 'Delete failed',
      createSuccess: 'Create successful',
      createFailed: 'Create failed',
      sendFailed: 'Send message failed'
    },
    "profile": {
      "common": {
        "add": "Add",
        "edit": "Edit",
        "delete": "Delete",
        "save": "Save Content",
        "cancel": "Cancel",
        "confirmDelete": "Are you sure you want to delete this record?",
        "requiredDesc": "Fields marked with * are required",
        "visibleInResume": "Show in resume",
        "visible": "Show",
        "hidden": "Hide",
        "dateFormat": "YYYY-MM-DD",
        "present": "Present",
        "empty": "No data yet, click the top right corner to add"
      },
      "project": {
        "title": "Project Experience Management",
        "addBtn": "Add New Project",
        "name": "Project Name",
        "namePlaceholder": "e.g., Enterprise Resume Builder Platform",
        "role": "Your Role",
        "rolePlaceholder": "e.g., Frontend Developer",
        "startDate": "Start Date",
        "endDate": "End Date",
        "endDatePlaceholder": "Optional, can be left blank if ongoing",
        "description": "Project Description",
        "descPlaceholder": "Briefly describe the project's goals and solutions",
        "contribution": "Key Contributions",
        "contriPlaceholder": "List your specific work and technical breakthroughs",
        "techStack": "Tech Stack",
        "highlights": "Project Highlights",
        "hlPlaceholder": "Optional, list outstanding outcomes or core challenges",
        "url": "Project Link",
        "urlPlaceholder": "Optional, e.g., GitHub or live demo URL",
        "addSuccess": "Project added successfully",
        "updateSuccess": "Project updated successfully",
        "deleteSuccess": "Project deleted successfully"
      },
      "education": {
        "title": "Education Experience Management",
        "addBtn": "Add Education",
        "school": "School Name",
        "schoolPlaceholder": "e.g., Tongji University",
        "major": "Major",
        "majorPlaceholder": "e.g., Software Engineering",
        "degree": "Degree",
        "startDate": "Start Date",
        "endDate": "End Date",
        "endDatePlaceholder": "Optional, can be left blank if currently enrolled",
        "gpa": "GPA/Grade",
        "gpaPlaceholder": "Optional, e.g., 3.8/4.0 or Top 10%",
        "addSuccess": "Education added successfully",
        "updateSuccess": "Education updated successfully",
        "deleteSuccess": "Education deleted successfully",
        "degrees": {
          "phd": "PhD",
          "master": "Master's",
          "bachelor": "Bachelor's",
          "associate": "Associate Degree",
          "highSchool": "High School",
          "other": "Other"
        }
      },
      career: {
        title: "Career Experience Management",
        addBtn: "Add Career Experience",
        company: "Company Name",
        companyPlaceholder: "e.g., ByteDance",
        position: "Position",
        positionPlaceholder: "e.g., Frontend Intern",
        startDate: "Start Date",
        endDate: "End Date",
        endDatePlaceholder: "Optional, leave blank if currently employed",
        details: "Responsibilities & Achievements",
        detailsPlaceholder: "Detail your tasks, technologies used, and outcomes",
        addSuccess: "Career experience added successfully",
        updateSuccess: "Career experience updated successfully",
        deleteSuccess: "Career experience deleted successfully"
      },
      skill: {
        title: "Professional Skills Management",
        addBtn: "Add Skill Set",
        name: "Skill Set Name",
        namePlaceholder: "e.g., Professional Skills",
        category: "Skill Category",
        categoryPlaceholder: "e.g., Frontend Frameworks",
        items: "Skill Details (Press Enter to add)",
        addCategory: "Add Skill Category",
        addSuccess: "Skill added successfully",
        updateSuccess: "Skill updated successfully",
        deleteSuccess: "Skill deleted successfully"
      },
      basicInfo: {
        title: "Basic Information",
        desc: "Please complete your basic info, which will be shown at the top of your resume",
        name: "Full Name",
        namePlaceholder: "Enter your real name",
        phone: "Phone Number",
        phonePlaceholder: "Enter your contact number",
        homepageUrl: "Personal Homepage / Blog URL",
        homepageUrlPlaceholder: "Optional, e.g., GitHub or personal website",
        updateSuccess: "Basic information saved successfully"
      },
      capability: {
        title: "Capability Profile",
        desc: "AI analyzes your resume and generates a detailed capability assessment report",
        regenerate: "Regenerate",
        generate: "Generate Profile",
        empty: "No capability profile data yet",
        summary: "Summary",
        competitiveness: "Overall Competitiveness",
        completeness: "Resume Completeness",
        totalScore: "Total Score",
        scoreDetail: "Position-Specific Assessment",
        abilityDetail: "Ability Details",
        strengths: "Strengths",
        missingSkills: "Missing Skills",
        weakEvidence: "Weak Evidence Items",
        suggestions: "Improvement Suggestions",
        generateMethod: "Generation Method",
        autoGenerate: "Auto Generate",
        autoGenerateDesc: "Automatically generate capability profile from your resume information (education, projects, skills, etc.)",
        fileUpload: "File Upload",
        fileUploadDesc: "Upload resume file (supports PDF, Word, TXT), AI will automatically analyze the file content",
        selectFile: "Select File",
        uploadOnGenerateHint: "File selected. It will be uploaded automatically when you generate or regenerate the profile.",
        uploadFile: "Upload File",
        uploadSuccess: "File uploaded successfully",
        textInput: "Text Input",
        textInputDesc: "Paste resume text content directly, AI will analyze the text to generate profile",
        textPlaceholder: "Please paste resume text content, including personal information, education, project experience, skills, etc...",
        fileUploadTips: "File Upload Tips",
        uploadTips: {
          useEnglishName: "Recommend using English filenames, avoid Chinese and special characters",
          fileSizeLimit: "Recommended file size under 10MB",
          tryTextInput: "If PDF parsing fails, try using 'Text Input' method"
        },
        selectFileFirst: "Please select a file to upload",
        fileInvalid: "Invalid file object, please select again",
        fileNameHasChinese: "Filename contains Chinese characters that backend cannot parse. Please rename to English (e.g., resume.pdf) and try again",
        onlySupportFormats: "Only supports PDF, TXT, DOC, DOCX formats",
        uploadSuccess2: "File uploaded successfully",
        uploadFailed: "File upload failed",
        uploadException: "File upload exception",
        checkNetwork: "Please check network connection",
        unknownError: "Unknown error",
        pleaseUploadFile: "Please upload a file first",
        urlInvalid: "Invalid file URL format, please re-upload the file",
        inputAtLeastChars: "Please enter at least 50 characters",
        completeResumeFirst: "Please complete your resume information (education, projects, skills, etc.) before generating capability profile",
        resumeTooShort: "Resume information is insufficient, please fill in education, projects, or skills before generating capability profile",
        generateSuccess: "Capability profile generated successfully",
        generateFailed: "Failed to generate capability profile",
        pdfParseFailed: "PDF parsing failed, please ensure:\n1. Valid PDF format\n2. No password protection\n3. File size under 10MB\n\nSuggestion: Try using 'Text Input' method by pasting resume content directly",
        fileProcessFailed: "File processing failed",
        loginToView: "Please login to view capability profile",
        goToLogin: "Go to Login"
      }
    },
    userSettings: {
      navTitle: 'User Settings',
      title: 'LLM Configuration',
      desc: 'Configure your own model and API Key. Platform default is used when not configured.',
      model: 'Model',
      modelPlaceholder: 'Select a model',
      modelRequired: 'Please select a model',
      apiKey: 'API Key',
      apiKeyPlaceholder: 'Enter API Key',
      apiKeyRequired: 'Please enter API Key',
      apiKeyHint: 'Key is encrypted after save. Re-enter the full key when editing.',
      save: 'Save',
      test: 'Test Connection',
      clearConfig: 'Clear Config',
      deleteConfirm: 'Clear your LLM config and revert to platform default?',
      saveSuccess: 'Configuration saved',
      deleteSuccess: 'Configuration cleared',
      testSuccess: 'Connection test succeeded',
      statusConfigured: 'Custom model configured',
      statusPlatformDefault: 'Using platform default',
      statusNotConfigured: 'Not configured',
      currentModel: 'Current model',
      currentApiKey: 'Current key'
    },
    resume: {
          template: 'Template',
          color: 'Theme Color',
          exportPdf: 'Export PDF',
          contact: 'Contact',
          education: 'Education',
          skills: 'Skills',
          experience: 'Experience',
          projects: 'Projects',
          present: 'Present',
          name: 'Your Name',
          targetJob: 'Target Role / Major',
          "manageContent": "Content Management",
          "saveSync": "Save & Sync to Cloud",
          "saveSuccess": "All modifications synced to cloud!",
          "saveFail": "Save failed, please check your network connection.",
          "basicInfo": "Basic Info",
          "fullName": "Full Name",
          "major": "Major",
          "phone": "Phone",
          "email": "Email",
          "school": "School / University",
          "startDate": "Start Date",
          "endDate": "End Date",
          "gpa": "GPA",
          "projectName": "Project Name",
          "role": "Role",
          "duration": "Duration",
          "projectDesc": "Description & Contributions (Bullet points recommended)",
          "company": "Company",
          "jobDetails": "Responsibilities & Achievements",
          "skillListName": "Skill List Name",
          "skillTip": "Manage specific skill items in the 'Personal Info' module. This section provides an overall layout preview.",
          "standardTemplate": "Standard",
          "modernTemplate": "Modern",
          "myResumes": "My Resumes",
          "subtitle": "Aggregate your experiences globally and generate professional layouts with one click.",
          "tagSmart": "SmartHire Standard",
          "statusReady": "Data Synchronized",
          "editResume": "Edit Resume",
          "emptyDesc": "You haven't generated a resume yet",
          "createBtn": "Create My Resume Now",
          "dialogDeleteTitle": "Reset Confirmation",
          "dialogDeleteContent": "Are you sure you want to clear this resume? Your underlying experience data (education, projects, etc.) will remain safe.",
          "dialogDeleteConfirm": "Confirm Reset",
          "msgDeleteSuccess": "Resume reset successfully",
          "msgDeleteFail": "Reset failed, please try again later.",
          "modalTitle": "Prerequisite: Select or Create Skill Profile",
          "tabSelect": "Select Existing Skills",
          "tabCreate": "New Skill List",
          "noSkillAvailable": "No skill lists available",
          "formLabelListName": "Name your skill list (e.g., Frontend Dev Special)",
          "formPlaceholderName": "Please input skill list name",
          "alertSkillTip": "Note: After creation, you can details skill stacks (Vue, React, etc.) in the 'Personal Info' module.",
          "modalConfirm": "Confirm & Enter Editor",
          "cancel": "Cancel",
          "getFail": "Failed to fetch data",
          "pleaseInput": "Please input",
          "createFail": "Creation failed",
          "position": "Position",
          "addEducation": "Add Education",
          "addProject": "Add Project",
          "addCareer": "Add Internship",
          "confirmAdd": "Confirm Add",
          "delete": "Delete",
          "confirmDeleteEdu": "Are you sure you want to delete this education record?",
          "confirmDeleteProj": "Are you sure you want to delete this project record?",
          "confirmDeleteCareer": "Are you sure you want to delete this internship record?",
          "missingIdParam": "Missing resume ID parameter",
          "contentNotFound": "Resume content not found",
          "degree": "Degree",
          "schoolLabel": "School",
          "majorLabel": "Major",
          "gpaLabel": "GPA",
          "projectNameLabel": "Project Name",
          "roleLabel": "Role",
          "startDateLabel": "Start Date",
          "endDateLabel": "End Date",
          "companyLabel": "Company",
          "positionLabel": "Position",
          "detailsLabel": "Job Details",
          "descLabel": "Description",
          "contributionLabel": "Contribution"
        },
    knowledge: {
      title: 'Knowledge Base Management',
      addBtn: 'Add Knowledge',
      searchPlaceholder: 'Search project name',
      name: 'Knowledge Name',
      namePlaceholder: 'Enter knowledge name',
      projectName: 'Project Name',
      projectNamePlaceholder: 'Enter project name',
      type: 'Knowledge Type',
      fileType: 'File Type',
      content: 'Content',
      contentPlaceholder: 'Enter content',
      tag: 'Knowledge Tags',
      status: 'Vectorization Status',
      addSuccess: 'Knowledge created successfully',
      updateSuccess: 'Knowledge updated successfully',
      viewDetail: 'View Details',
      unknown: 'Unknown',
      createFirst: 'Create First Knowledge Document',
      docTitle: 'Document Title',
      docTitlePlaceholder: 'Please enter document title',
      uploadFile: 'Document File',
      uploadTip: 'Support PDF and Markdown files',
      dragUpload: 'Drag file here or click to upload',
      dragUploadActive: 'Release to upload',
      orInputUrl: 'Or enter URL directly',
      inputUrlPlaceholder: 'Enter COS file URL (support .pdf or .md)',
      githubRepoLink: 'GitHub Repository Link',
      githubRepoPlaceholder: 'Enter GitHub repo link, e.g. https://github.com/username/repo',
      githubRepoExample: 'Example: https://github.com/username/repository',
      basicInfo: 'Basic Information',
      vectorInfo: 'Vectorization Info',
      lastVectorTime: 'Last Vectorization Time',
      errorMsg: 'Error Message',
      openFile: 'Open File',
      viewRepo: 'View Repo',
      downloadFile: 'Download File',
      typeOptions: {
        projectDoc: 'Project Doc',
        projectCode: 'Project Code',
        techDoc: 'Tech Doc',
        other: 'Other',
        deepWiki: 'DeepWiki'
      },
      fileTypeOptions: {
        txt: 'Text (TXT)',
        url: 'URL',
        doc: 'Document (DOC/PDF)',
        md: 'Markdown (MD)'
      },
      statusOptions: {
        pending: 'Pending',
        processing: 'Processing',
        success: 'Completed',
        failed: 'Failed',
        cancelled: 'Cancelled'
      },
      deleteSuccess: 'Deleted successfully',
      typeAll: 'All',
      typeProjectDoc: 'Project Doc',
      typeGithub: 'GitHub Code',
      embeddingPending: 'Pending',
      embeddingRunning: 'Vectorizing...',
      embeddingSuccess: 'Vectorized',
      embeddingFailed: 'Vectorization Failed'
    },
    jobs: {
      title: "Job Information",
      create: "Create Job",
      edit: "Edit Job",
      delete: "Delete Job",
      viewDetail: "View Details",
      generateProfile: "Generate Capability Profile",
      regenerateProfile: "Regenerate Profile",
      searchJobName: "Search job name",
      searchCompanyName: "Search company name",
      filterTitle: "Filters",
      toggleFilters: "Filters",
      filterAll: "All",
      salaryTypePlaceholder: "Select salary unit",
      salaryFilterHint: "Select employment or salary type before entering a salary range (same unit as selected type).",
      employmentType: "Employment Type",
      intern: "Intern",
      fulltime: "Full-time",
      basicInfo: "Basic Information",
      jobName: "Job Title",
      companyName: "Company Name",
      companyIndustry: "Company Industry",
      location: "Location",
      salaryInfo: "Salary Information",
      salaryMin: "Minimum Salary",
      salaryMax: "Maximum Salary",
      salaryType: "Salary Type",
      daily: "Daily",
      monthly: "Monthly",
      yearly: "Yearly",
      jobDescription: "Job Description",
      jobDuties: "Job Responsibilities",
      jobRequirements: "Job Requirements",
      jobKeywords: "Job Keywords",
      otherInfo: "Other Information",
      jobLink: "Job Link",
      companyIntro: "Company Introduction",
      capabilityProfile: "Job Capability Profile",
      technicalSkills: "Technical Skills",
      softSkills: "Soft Skills",
      toolUsage: "Tool Usage",
      domainKnowledge: "Domain Knowledge",
      suggestions: "Suggestions",
      noProfile: "No capability profile",
      confirmDelete: "Confirm Delete",
      confirmDeleteContent: "Are you sure you want to delete this job?",
      deleteSuccess: "Deleted successfully",
      deleteFailed: "Delete failed",
      createSuccess: "Created successfully",
      updateSuccess: "Updated successfully",
      createFailed: "Create failed",
      updateFailed: "Update failed",
      loadFailed: "Load failed",
      formValidation: {
        jobNameRequired: "Please enter job title",
        companyNameRequired: "Please enter company name",
        descriptionRequired: "Please enter job description",
        locationRequired: "Please enter location",
        salaryMinRequired: "Please enter minimum salary",
        salaryMaxRequired: "Please enter maximum salary",
        salaryTypeRequired: "Please select salary type",
        linkRequired: "Please enter job link",
        salaryRangeInvalid: "Minimum salary cannot be greater than maximum salary"
      },
      placeholders: {
        jobName: "Please enter job title",
        companyName: "Please enter company name",
        location: "Please enter location, e.g.: Beijing, Shanghai, Remote",
        description: "Please enter job description, including job background, responsibilities, etc.",
        duties: "Enter one responsibility per line",
        requirements: "Enter one requirement per line",
        keywords: "Enter one keyword per line",
        link: "Please enter job link, e.g.: BOSS Zhipin, Lagou, etc.",
        companyIntro: "Please enter company introduction (optional)",
        industries: "Enter one industry per line"
      },
      tips: {
        dutiesFormat: "Enter one responsibility per line, e.g.: Responsible for backend system design and development",
        requirementsFormat: "Enter one requirement per line, e.g.: Bachelor degree or above",
        keywordsFormat: "Support comma, space or newline to separate keywords",
        industriesFormat: "Enter one industry tag per line"
      },
      empty: "No job data",
      createFirst: "Create first job",
      profileGenerating: "Generating capability profile...",
      profileGenerated: "Capability profile generated successfully",
      loadProfileFailed: "Failed to load capability profile",
      generateProfileError: "Error generating capability profile",
      retryLater: ", please try again later",
      jobType: "Job Type",
      certificateRequired: "Certificate Requirements",
      innovationAbility: "Innovation Ability",
      learningAbility: "Learning Ability",
      pressureResistance: "Stress Resistance",
      communicationAbility: "Communication Ability",
      practicalAbility: "Practical Ability",
      strengths: "Job Strengths",
      missingSkills: "Missing Skills",
      weakEvidenceItems: "Weak Evidence Items",
      summary: "Summary",
      infoSuffix: " Information",
      industryLabel: "Industry: ",
      matchAnalyzing: "Analyzing job-candidate match, estimated 30s~1min…",
      matchSuccess: "Job-candidate match analysis complete",
      matchFailed: "Job-candidate match analysis failed",
      matchFailedRetry: "Job-candidate match analysis failed, please try again later",
      reanalyzeMatch: "Re-analyze Match",
      analyzeMatch: "Analyze Match",
      overallMatchScore: "Overall Match",
      matchSummaryLabel: "Match Summary",
      matchHighlightsLabel: "Matched Highlights",
      matchDimensions: {
        basic: "Basic Requirements",
        professionalSkill: "Professional Skills",
        professionalQuality: "Professional Quality",
        developmentPotential: "Development Potential"
      },
      careerReport: {
        entry: "Career Report",
        generate: "Generate Career Report",
        regenerate: "Regenerate Report",
        view: "View Report",
        drawerTitle: "Career Development Report",
        empty: "No career report yet. Click to generate.",
        generating: "Generating career report, this may take 1-3 minutes…",
        generateSuccess: "Career report generated",
        generateFailed: "Failed to generate career report",
        gatewayTimeoutFallback: "Gateway dropped the request (504); the backend may still be working. Polling latest version…",
        gatewayTimeoutHint: "Gateway timeout (504). Ask backend to raise Nginx proxy_read_timeout for /api/zdmj/career-reports/ to 600s.",
        polish: "Polish",
        polishing: "Polishing report…",
        polishSuccess: "Polished (new version saved)",
        polishFailed: "Polish failed",
        check: "Integrity Check",
        checking: "Checking integrity…",
        checkSuccess: "Integrity check completed",
        checkFailed: "Integrity check failed",
        version: "Version",
        latest: "Latest",
        completenessScore: "Completeness",
        statusLabel: "Status",
        status: {
          draft: "Draft",
          checked: "Checked",
          published: "Published",
          checkFailed: "Check Failed",
          unknown: "Unknown"
        },
        riskLevel: "Risk Level",
        riskLow: "Low",
        riskMedium: "Medium",
        riskHigh: "High",
        missingSections: "Missing Sections",
        nonActionableItems: "Non-actionable Items",
        weakEvidenceItems: "Weak Evidence Items",
        knowledgeSources: "Knowledge Sources",
        reportContent: "Report Content",
        userPreferenceLabel: "Preference",
        userPreferencePlaceholder: "Optional: city / industry / role tendency",
        focusLabel: "Focus",
        focusPlaceholder: "Optional: e.g. \"strengthen algorithm side\"",
        polishInstructionLabel: "Polish Instruction",
        polishInstructionPlaceholder: "Optional: e.g. \"more concise\" / \"more actionable\"",
        confirmGenerate: "Generate",
        confirmPolish: "Polish",
        cancel: "Cancel"
      }
    }
  },
  form: {
    required: 'Cannot be empty',
    userName: {
      required: 'Please enter user name',
      invalid: 'User name format is incorrect'
    },
    phone: {
      required: 'Please enter phone number',
      invalid: 'Phone number format is incorrect'
    },
    pwd: {
      required: 'Please enter password',
      invalid: '6-18 characters, including letters, numbers, and underscores'
    },
    confirmPwd: {
      required: 'Please enter password again',
      invalid: 'The two passwords are inconsistent'
    },
    code: {
      required: 'Please enter verification code',
      invalid: 'Verification code format is incorrect'
    },
    email: {
      required: 'Please enter email',
      invalid: 'Email format is incorrect'
    }
  },
  dropdown: {
    closeCurrent: 'Close Current',
    closeOther: 'Close Other',
    closeLeft: 'Close Left',
    closeRight: 'Close Right',
    closeAll: 'Close All'
  },
  icon: {
    themeConfig: 'Theme Configuration',
    themeSchema: 'Theme Schema',
    lang: 'Switch Language',
    fullscreen: 'Fullscreen',
    fullscreenExit: 'Exit Fullscreen',
    reload: 'Reload Page',
    collapse: 'Collapse Menu',
    expand: 'Expand Menu',
    pin: 'Pin',
    unpin: 'Unpin'
  },
  datatable: {
    itemCount: 'Total {total} items'
  }
};

export default local;
